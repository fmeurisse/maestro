package io.maestro.plugins.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.errors.ExecutionNotFoundException
import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ErrorInfo
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.ExecutionStepResult
import io.maestro.model.execution.StepStatus
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.jdbi.v3.core.Jdbi
import java.time.Instant

/**
 * PostgreSQL implementation of IWorkflowExecutionRepository.
 *
 * Uses JDBI for database access with per-step transaction commits (checkpoint pattern).
 *
 * Features:
 * - Per-step commits for crash recovery (SC-002 compliance)
 * - JSONB storage for input_parameters, input_data, output_data, error_details
 * - Efficient queries with indexes on status, revision, and timestamps
 */
@ApplicationScoped
class PostgresWorkflowExecutionRepository @Inject constructor(
    private val jdbi: Jdbi,
    @Named("jsonbObjectMapper") private val objectMapper: ObjectMapper
) : IWorkflowExecutionRepository {

    private val logger = KotlinLogging.logger {}

    private val jsonMapper = objectMapper.apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        // Ensure timestamps are serialized as ISO-8601 strings for consistency
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override fun createExecution(execution: WorkflowExecution): WorkflowExecution {
        logger.debug { "Creating workflow execution: ${execution.executionId}" }

        return jdbi.withHandle<WorkflowExecution, Exception> { handle ->
            // Check if execution already exists
            val exists = handle.createQuery("""
                SELECT COUNT(*) FROM workflow_executions
                WHERE execution_id = :executionId
            """.trimIndent())
                .bind("executionId", execution.executionId.value)
                .mapTo(Int::class.java)
                .one()

            if (exists > 0) {
                throw IllegalArgumentException("Execution already exists: ${execution.executionId}")
            }

            // Serialize input parameters to JSONB
            val inputParamsJson = jsonMapper.writeValueAsString(execution.inputParameters)

            // Insert execution record
            handle.createUpdate("""
                INSERT INTO workflow_executions (
                    execution_id, revision_namespace, revision_id, revision_version,
                    input_parameters, status, error_message,
                    started_at, completed_at, last_updated_at
                ) VALUES (
                    :executionId, :namespace, :revisionId, :version,
                    :inputParameters::jsonb, :status, :errorMessage,
                    :startedAt, :completedAt, :lastUpdatedAt
                )
            """.trimIndent())
                .bind("executionId", execution.executionId.value)
                .bind("namespace", execution.revisionId.namespace)
                .bind("revisionId", execution.revisionId.id)
                .bind("version", execution.revisionId.version)
                .bind("inputParameters", inputParamsJson)
                .bind("status", execution.status.name)
                .bind("errorMessage", execution.errorMessage)
                .bind("startedAt", execution.startedAt)
                .bind("completedAt", execution.completedAt)
                .bind("lastUpdatedAt", execution.lastUpdatedAt)
                .execute()

            logger.debug { "Successfully created workflow execution: ${execution.executionId}" }
            execution
        }
    }

    override fun saveStepResult(stepResult: ExecutionStepResult): ExecutionStepResult {
        logger.debug {
            "Saving step result for execution ${stepResult.executionId}, step ${stepResult.stepIndex}"
        }

        return jdbi.withHandle<ExecutionStepResult, Exception> { handle ->
            // Check for duplicate stepIndex
            val exists = handle.createQuery("""
                SELECT COUNT(*) FROM execution_step_results
                WHERE execution_id = :executionId AND step_index = :stepIndex
            """.trimIndent())
                .bind("executionId", stepResult.executionId.value)
                .bind("stepIndex", stepResult.stepIndex)
                .mapTo(Int::class.java)
                .one()

            if (exists > 0) {
                throw IllegalArgumentException(
                    "Step result already exists for execution ${stepResult.executionId}, step ${stepResult.stepIndex}"
                )
            }

            // Serialize JSONB fields
            val inputDataJson = stepResult.inputData?.let { jsonMapper.writeValueAsString(it) }
            val outputDataJson = stepResult.outputData?.let { jsonMapper.writeValueAsString(it) }
            val errorDetailsJson = stepResult.errorDetails?.let { jsonMapper.writeValueAsString(it) }

            // Insert step result (per-step commit for checkpoint pattern)
            handle.createUpdate("""
                INSERT INTO execution_step_results (
                    result_id, execution_id, step_index, step_id, step_type, status,
                    input_data, output_data, error_message, error_details,
                    started_at, completed_at
                ) VALUES (
                    :resultId, :executionId, :stepIndex, :stepId, :stepType, :status,
                    :inputData::jsonb, :outputData::jsonb, :errorMessage, :errorDetails::jsonb,
                    :startedAt, :completedAt
                )
            """.trimIndent())
                .bind("resultId", stepResult.resultId)
                .bind("executionId", stepResult.executionId.value)
                .bind("stepIndex", stepResult.stepIndex)
                .bind("stepId", stepResult.stepId)
                .bind("stepType", stepResult.stepType)
                .bind("status", stepResult.status.name)
                .bind("inputData", inputDataJson)
                .bind("outputData", outputDataJson)
                .bind("errorMessage", stepResult.errorMessage)
                .bind("errorDetails", errorDetailsJson)
                .bind("startedAt", stepResult.startedAt)
                .bind("completedAt", stepResult.completedAt)
                .execute()

            // Commit immediately (per-step commit for checkpoint pattern)
            handle.commit()

            logger.debug {
                "Successfully saved step result for execution ${stepResult.executionId}, step ${stepResult.stepIndex}"
            }
            stepResult
        }
    }

    override fun updateExecutionStatus(
        executionId: WorkflowExecutionID,
        status: ExecutionStatus,
        errorMessage: String?
    ) {
        logger.debug { "Updating execution status: $executionId -> $status" }

        jdbi.useHandle<Exception> { handle ->
            val now = Instant.now()

            // Determine completedAt based on status
            val completedAt = if (status.isTerminal()) now else null

            val rowsUpdated = handle.createUpdate("""
                UPDATE workflow_executions
                SET status = :status,
                    error_message = :errorMessage,
                    completed_at = :completedAt,
                    last_updated_at = :lastUpdatedAt
                WHERE execution_id = :executionId
            """.trimIndent())
                .bind("executionId", executionId.value)
                .bind("status", status.name)
                .bind("errorMessage", errorMessage)
                .bind("completedAt", completedAt)
                .bind("lastUpdatedAt", now)
                .execute()

            if (rowsUpdated == 0) {
                throw ExecutionNotFoundException(executionId)
            }

            logger.debug { "Successfully updated execution status: $executionId -> $status" }
        }
    }

    override fun findById(executionId: WorkflowExecutionID): WorkflowExecution? {
        logger.debug { "Finding execution by ID: $executionId" }

        return jdbi.withHandle<WorkflowExecution?, Exception> { handle ->
            // Query execution
            val executionRow = handle.createQuery("""
                SELECT execution_id, revision_namespace, revision_id, revision_version,
                       input_parameters, status, error_message,
                       started_at, completed_at, last_updated_at
                FROM workflow_executions
                WHERE execution_id = :executionId
            """.trimIndent())
                .bind("executionId", executionId.value)
                .map { rs, _ ->
                    val inputParamsJson = rs.getString("input_parameters")
                    val inputParams = jsonMapper.readValue<Map<String, Any>>(inputParamsJson)

                    WorkflowExecution(
                        executionId = WorkflowExecutionID.Companion.fromString(rs.getString("execution_id")),
                        revisionId = WorkflowRevisionID(
                            namespace = rs.getString("revision_namespace"),
                            id = rs.getString("revision_id"),
                            version = rs.getInt("revision_version")
                        ),
                        inputParameters = inputParams,
                        status = ExecutionStatus.valueOf(rs.getString("status")),
                        errorMessage = rs.getString("error_message"),
                        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
                        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                        lastUpdatedAt = rs.getTimestamp("last_updated_at")!!.toInstant()
                    )
                }
                .findFirst()
                .orElse(null)

            if (executionRow == null) {
                return@withHandle null
            }

            // Eagerly load all step results ordered by stepIndex
            val stepResults = handle.createQuery("""
                SELECT result_id, execution_id, step_index, step_id, step_type, status,
                       input_data, output_data, error_message, error_details,
                       started_at, completed_at
                FROM execution_step_results
                WHERE execution_id = :executionId
                ORDER BY step_index ASC
            """.trimIndent())
                .bind("executionId", executionId.value)
                .map { rs, _ ->
                    val inputDataJson = rs.getString("input_data")
                    val inputData = if (inputDataJson != null) {
                        jsonMapper.readValue<Map<String, Any>>(inputDataJson)
                    } else null

                    val outputDataJson = rs.getString("output_data")
                    val outputData = if (outputDataJson != null) {
                        jsonMapper.readValue<Map<String, Any>>(outputDataJson)
                    } else null

                    val errorDetailsJson = rs.getString("error_details")
                    val errorDetails = if (errorDetailsJson != null) {
                        jsonMapper.readValue<ErrorInfo>(errorDetailsJson)
                    } else null

                    ExecutionStepResult(
                        resultId = rs.getString("result_id"),
                        executionId = WorkflowExecutionID.Companion.fromString(rs.getString("execution_id")),
                        stepIndex = rs.getInt("step_index"),
                        stepId = rs.getString("step_id"),
                        stepType = rs.getString("step_type"),
                        status = StepStatus.valueOf(rs.getString("status")),
                        inputData = inputData,
                        outputData = outputData,
                        errorMessage = rs.getString("error_message"),
                        errorDetails = errorDetails,
                        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
                        completedAt = rs.getTimestamp("completed_at")!!.toInstant()
                    )
                }
                .list()

            // Note: WorkflowExecution doesn't have a stepResults field in the model
            // Step results are loaded separately and returned via the use case layer
            executionRow
        }
    }

    override fun findByWorkflowRevision(
        revisionId: WorkflowRevisionID,
        status: ExecutionStatus?,
        limit: Int,
        offset: Int
    ): List<WorkflowExecution> {
        logger.debug {
            "Finding executions for revision $revisionId, status=$status, limit=$limit, offset=$offset"
        }

        require(limit > 0) { "Limit must be positive, got: $limit" }
        require(limit <= 100) { "Limit must be <= 100, got: $limit" }
        require(offset >= 0) { "Offset must be >= 0, got: $offset" }

        return jdbi.withHandle<List<WorkflowExecution>, Exception> { handle ->
            val statusFilter = if (status != null) "AND status = :status" else ""

            handle.createQuery("""
                SELECT execution_id, revision_namespace, revision_id, revision_version,
                       input_parameters, status, error_message,
                       started_at, completed_at, last_updated_at
                FROM workflow_executions
                WHERE revision_namespace = :namespace
                  AND revision_id = :revisionId
                  AND revision_version = :version
                  $statusFilter
                ORDER BY started_at DESC
                LIMIT :limit OFFSET :offset
            """.trimIndent())
                .bind("namespace", revisionId.namespace)
                .bind("revisionId", revisionId.id)
                .bind("version", revisionId.version)
                .apply { if (status != null) bind("status", status.name) }
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ ->
                    val inputParamsJson = rs.getString("input_parameters")
                    val inputParams = jsonMapper.readValue<Map<String, Any>>(inputParamsJson)

                    WorkflowExecution(
                        executionId = WorkflowExecutionID.Companion.fromString(rs.getString("execution_id")),
                        revisionId = WorkflowRevisionID(
                            namespace = rs.getString("revision_namespace"),
                            id = rs.getString("revision_id"),
                            version = rs.getInt("revision_version")
                        ),
                        inputParameters = inputParams,
                        status = ExecutionStatus.valueOf(rs.getString("status")),
                        errorMessage = rs.getString("error_message"),
                        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
                        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                        lastUpdatedAt = rs.getTimestamp("last_updated_at")!!.toInstant()
                    )
                }
                .list()
        }
    }

    override fun countByWorkflowRevision(
        revisionId: WorkflowRevisionID,
        status: ExecutionStatus?
    ): Long {
        logger.debug { "Counting executions for revision $revisionId, status=$status" }

        return jdbi.withHandle<Long, Exception> { handle ->
            val statusFilter = if (status != null) "AND status = :status" else ""

            handle.createQuery("""
                SELECT COUNT(*) FROM workflow_executions
                WHERE revision_namespace = :namespace
                  AND revision_id = :revisionId
                  AND revision_version = :version
                  $statusFilter
            """.trimIndent())
                .bind("namespace", revisionId.namespace)
                .bind("revisionId", revisionId.id)
                .bind("version", revisionId.version)
                .apply { if (status != null) bind("status", status.name) }
                .mapTo(Long::class.java)
                .one()
        }
    }

    override fun findByWorkflow(
        namespace: String,
        workflowId: String,
        version: Int?,
        status: ExecutionStatus?,
        limit: Int,
        offset: Int
    ): List<WorkflowExecution> {
        logger.debug {
            "Finding executions for workflow $namespace/$workflowId, version=$version, status=$status, limit=$limit, offset=$offset"
        }

        require(limit > 0) { "Limit must be positive, got: $limit" }
        require(limit <= 100) { "Limit must be <= 100, got: $limit" }
        require(offset >= 0) { "Offset must be >= 0, got: $offset" }

        return jdbi.withHandle<List<WorkflowExecution>, Exception> { handle ->
            val versionFilter = if (version != null) "AND revision_version = :version" else ""
            val statusFilter = if (status != null) "AND status = :status" else ""

            handle.createQuery("""
                SELECT execution_id, revision_namespace, revision_id, revision_version,
                       input_parameters, status, error_message,
                       started_at, completed_at, last_updated_at
                FROM workflow_executions
                WHERE revision_namespace = :namespace
                  AND revision_id = :workflowId
                  $versionFilter
                  $statusFilter
                ORDER BY started_at DESC
                LIMIT :limit OFFSET :offset
            """.trimIndent())
                .bind("namespace", namespace)
                .bind("workflowId", workflowId)
                .apply { if (version != null) bind("version", version) }
                .apply { if (status != null) bind("status", status.name) }
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ ->
                    val inputParamsJson = rs.getString("input_parameters")
                    val inputParams = jsonMapper.readValue<Map<String, Any>>(inputParamsJson)

                    WorkflowExecution(
                        executionId = WorkflowExecutionID.Companion.fromString(rs.getString("execution_id")),
                        revisionId = WorkflowRevisionID(
                            namespace = rs.getString("revision_namespace"),
                            id = rs.getString("revision_id"),
                            version = rs.getInt("revision_version")
                        ),
                        inputParameters = inputParams,
                        status = ExecutionStatus.valueOf(rs.getString("status")),
                        errorMessage = rs.getString("error_message"),
                        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
                        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                        lastUpdatedAt = rs.getTimestamp("last_updated_at")!!.toInstant()
                    )
                }
                .list()
        }
    }

    override fun countByWorkflow(
        namespace: String,
        workflowId: String,
        version: Int?,
        status: ExecutionStatus?
    ): Long {
        logger.debug { "Counting executions for workflow $namespace/$workflowId, version=$version, status=$status" }

        return jdbi.withHandle<Long, Exception> { handle ->
            val versionFilter = if (version != null) "AND revision_version = :version" else ""
            val statusFilter = if (status != null) "AND status = :status" else ""

            handle.createQuery("""
                SELECT COUNT(*) FROM workflow_executions
                WHERE revision_namespace = :namespace
                  AND revision_id = :workflowId
                  $versionFilter
                  $statusFilter
            """.trimIndent())
                .bind("namespace", namespace)
                .bind("workflowId", workflowId)
                .apply { if (version != null) bind("version", version) }
                .apply { if (status != null) bind("status", status.name) }
                .mapTo(Long::class.java)
                .one()
        }
    }

    override fun findStepResultsByExecutionId(executionId: WorkflowExecutionID): List<ExecutionStepResult> {
        logger.debug { "Finding step results for execution: $executionId" }

        return jdbi.withHandle<List<ExecutionStepResult>, Exception> { handle ->
            handle.createQuery("""
                SELECT result_id, execution_id, step_index, step_id, step_type, status,
                       input_data, output_data, error_message, error_details,
                       started_at, completed_at
                FROM execution_step_results
                WHERE execution_id = :executionId
                ORDER BY step_index ASC
            """.trimIndent())
                .bind("executionId", executionId.value)
                .map { rs, _ ->
                    val inputDataJson = rs.getString("input_data")
                    val inputData = if (inputDataJson != null) {
                        jsonMapper.readValue<Map<String, Any>>(inputDataJson)
                    } else null

                    val outputDataJson = rs.getString("output_data")
                    val outputData = if (outputDataJson != null) {
                        jsonMapper.readValue<Map<String, Any>>(outputDataJson)
                    } else null

                    val errorDetailsJson = rs.getString("error_details")
                    val errorDetails = if (errorDetailsJson != null) {
                        jsonMapper.readValue<ErrorInfo>(errorDetailsJson)
                    } else null

                    ExecutionStepResult(
                        resultId = rs.getString("result_id"),
                        executionId = WorkflowExecutionID.Companion.fromString(rs.getString("execution_id")),
                        stepIndex = rs.getInt("step_index"),
                        stepId = rs.getString("step_id"),
                        stepType = rs.getString("step_type"),
                        status = StepStatus.valueOf(rs.getString("status")),
                        inputData = inputData,
                        outputData = outputData,
                        errorMessage = rs.getString("error_message"),
                        errorDetails = errorDetails,
                        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
                        completedAt = rs.getTimestamp("completed_at")!!.toInstant()
                    )
                }
                .list()
            }
    }
}