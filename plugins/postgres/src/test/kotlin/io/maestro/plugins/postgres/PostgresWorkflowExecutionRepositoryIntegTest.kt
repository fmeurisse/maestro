package io.maestro.plugins.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.maestro.core.errors.ExecutionNotFoundException
import io.maestro.core.workflows.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.ExecutionStepResult
import io.maestro.model.execution.StepStatus
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import io.maestro.model.util.NanoID
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jdbi.v3.core.Jdbi
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Instant

/**
 * Integration tests for PostgresWorkflowExecutionRepository using Kotest and Testcontainers.
 *
 * Tests cover:
 * - Creating workflow executions
 * - Saving step results (per-step commits)
 * - Updating execution status
 * - Finding executions by ID
 * - Querying executions by workflow revision
 * - Finding step results by execution ID
 */
class PostgresWorkflowExecutionRepositoryIntegTest : FeatureSpec({

    // PostgreSQL container using Kotest Testcontainers extension
    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        .withDatabaseName("maestro_test")
        .withUsername("test")
        .withPassword("test")

    // Register the container extension (per test lifecycle)
    extensions(postgres.perSpec())

    // Setup variables
    lateinit var jdbi: Jdbi
    lateinit var objectMapper: ObjectMapper
    lateinit var repository: PostgresWorkflowExecutionRepository

    beforeSpec {
        // Configure JDBI
        jdbi = Jdbi.create(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )

        // Configure ObjectMapper for JSONB serialization
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            // Serialize Instant as ISO-8601 strings instead of numeric timestamps
            // This ensures PostgreSQL can parse them correctly in the trigger function
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        // Run Liquibase migrations
        val connection = DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
        try {
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))

            val commandScope = CommandScope("update")
            commandScope.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG.getName(), "db/changelog/db.changelog-master.xml")
            commandScope.addArgumentValue("database", database)
            commandScope.addArgumentValue("resourceAccessor", ClassLoaderResourceAccessor())
            commandScope.execute()
        } finally {
            connection.close()
        }

        // Create repository instance
        repository = PostgresWorkflowExecutionRepository(jdbi, objectMapper)
    }

    beforeEach {
        // Clean database before each test to ensure isolation
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM execution_step_results").execute()
            handle.createUpdate("DELETE FROM workflow_executions").execute()
            handle.createUpdate("DELETE FROM workflow_revisions").execute()
        }
    }

    /**
     * Helper function to create a workflow revision in the database.
     * This is needed because workflow_executions has a foreign key constraint
     * that requires the referenced workflow revision to exist.
     */
    fun createTestWorkflowRevision(
        namespace: String = "test-ns",
        id: String = "test-wf",
        version: Int = 1,
        name: String = "Test Workflow",
        description: String = "Test description"
    ) {
        val revision = WorkflowRevision.Companion.validateAndCreate(
            namespace = namespace,
            id = id,
            version = version,
            name = name,
            description = description,
            steps = listOf(LogTask("Test log message")),
            active = false,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Serialize WorkflowRevision to JSON (without yamlSource field)
        val revisionJson = objectMapper.writeValueAsString(revision)

        // Insert into database
        jdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate("""
                INSERT INTO workflow_revisions (yaml_source, revision_data)
                VALUES (:yamlSource, :revisionData::jsonb)
                ON CONFLICT (namespace, id, version) DO NOTHING
            """.trimIndent())
                .bind("yamlSource", "namespace: $namespace\nid: $id\nname: $name\ndescription: $description\nsteps:\n  - type: LogTask\n    message: Test log message")
                .bind("revisionData", revisionJson)
                .execute()
        }
    }

    feature("Creating workflow executions") {
        scenario("should create execution with RUNNING status") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val inputParams = mapOf("userId" to "user123", "retryCount" to 3)
            val now = Instant.now()

            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = inputParams,
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = now,
                completedAt = null,
                lastUpdatedAt = now
            )

            // When
            val created = repository.createExecution(execution)

            // Then
            created shouldBe execution
            val found = repository.findById(executionId)
            found.shouldNotBeNull()
            found.executionId shouldBe executionId
            found.revisionId shouldBe revisionId
            found.inputParameters shouldBe inputParams
            found.status shouldBe ExecutionStatus.RUNNING
            found.errorMessage.shouldBeNull()
            found.completedAt.shouldBeNull()
        }

        scenario("should reject duplicate execution IDs") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )

            repository.createExecution(execution)

            // When/Then
            shouldThrow<IllegalArgumentException> {
                repository.createExecution(execution)
            }
        }
    }

    feature("Saving step results") {
        scenario("should save step result with per-step commit") {
            // Given: An execution
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(execution)

            // When: Saving a step result
            val stepResult = ExecutionStepResult(
                resultId = NanoID.generate(),
                executionId = executionId,
                stepIndex = 0,
                stepId = "step-1",
                stepType = "LogTask",
                status = StepStatus.COMPLETED,
                inputData = mapOf("input" to "value"),
                outputData = mapOf("output" to "result"),
                errorMessage = null,
                errorDetails = null,
                startedAt = Instant.now().minusSeconds(5),
                completedAt = Instant.now()
            )
            val saved = repository.saveStepResult(stepResult)

            // Then
            saved shouldBe stepResult
            val stepResults = repository.findStepResultsByExecutionId(executionId)
            stepResults shouldHaveSize 1
            stepResults[0].stepIndex shouldBe 0
            stepResults[0].stepId shouldBe "step-1"
            stepResults[0].status shouldBe StepStatus.COMPLETED
        }

        scenario("should reject duplicate step indexes") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(execution)

            val stepResult1 = ExecutionStepResult(
                resultId = NanoID.generate(),
                executionId = executionId,
                stepIndex = 0,
                stepId = "step-1",
                stepType = "LogTask",
                status = StepStatus.COMPLETED,
                inputData = null,
                outputData = null,
                errorMessage = null,
                errorDetails = null,
                startedAt = Instant.now(),
                completedAt = Instant.now()
            )
            repository.saveStepResult(stepResult1)

            // When/Then: Same stepIndex should fail
            val stepResult2 = stepResult1.copy(resultId = NanoID.generate())
            shouldThrow<IllegalArgumentException> {
                repository.saveStepResult(stepResult2)
            }
        }
    }

    feature("Updating execution status") {
        scenario("should update status to COMPLETED") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(execution)

            // When
            repository.updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.COMPLETED,
                errorMessage = null
            )

            // Then
            val updated = repository.findById(executionId)
            updated.shouldNotBeNull()
            updated.status shouldBe ExecutionStatus.COMPLETED
            updated.completedAt.shouldNotBeNull()
            updated.errorMessage.shouldBeNull()
        }

        scenario("should update status to FAILED with error message") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(execution)

            // When
            repository.updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                errorMessage = "Step execution failed: NullPointerException"
            )

            // Then
            val updated = repository.findById(executionId)
            updated.shouldNotBeNull()
            updated.status shouldBe ExecutionStatus.FAILED
            updated.completedAt.shouldNotBeNull()
            updated.errorMessage shouldBe "Step execution failed: NullPointerException"
        }

        scenario("should throw exception for non-existent execution") {
            // Given
            val nonExistentId = WorkflowExecutionID.Companion.generate()

            // When/Then
            shouldThrow<ExecutionNotFoundException> {
                repository.updateExecutionStatus(
                    executionId = nonExistentId,
                    status = ExecutionStatus.COMPLETED,
                    errorMessage = null
                )
            }
        }
    }

    feature("Finding executions") {
        scenario("should return null for non-existent execution") {
            // Given
            val nonExistentId = WorkflowExecutionID.Companion.generate()

            // When
            val result = repository.findById(nonExistentId)

            // Then
            result.shouldBeNull()
        }

        scenario("should find execution with step results") {
            // Given
            createTestWorkflowRevision()
            val executionId = WorkflowExecutionID.Companion.generate()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            val execution = WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = mapOf("key" to "value"),
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = null,
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(execution)

            // Add step results
            repository.saveStepResult(
                ExecutionStepResult(
                    resultId = NanoID.generate(),
                    executionId = executionId,
                    stepIndex = 0,
                    stepId = "step-1",
                    stepType = "LogTask",
                    status = StepStatus.COMPLETED,
                    inputData = null,
                    outputData = null,
                    errorMessage = null,
                    errorDetails = null,
                    startedAt = Instant.now(),
                    completedAt = Instant.now()
                )
            )

            // When
            val found = repository.findById(executionId)
            val stepResults = repository.findStepResultsByExecutionId(executionId)

            // Then
            found.shouldNotBeNull()
            stepResults shouldHaveSize 1
        }
    }

    feature("Querying executions by revision") {
        scenario("should find executions for a workflow revision") {
            // Given: Multiple executions for same revision
            createTestWorkflowRevision()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)

            repeat(3) {
                val execution = WorkflowExecution(
                    executionId = WorkflowExecutionID.Companion.generate(),
                    revisionId = revisionId,
                    inputParameters = emptyMap(),
                    status = ExecutionStatus.COMPLETED,
                    errorMessage = null,
                    startedAt = Instant.now().minusSeconds((3 - it).toLong()),
                    completedAt = Instant.now(),
                    lastUpdatedAt = Instant.now()
                )
                repository.createExecution(execution)
            }

            // When
            val executions = repository.findByWorkflowRevision(revisionId)

            // Then
            executions shouldHaveSize 3
            // Should be sorted by startedAt DESC
            executions[0].startedAt.isAfter(executions[1].startedAt) shouldBe true
        }

        scenario("should filter by execution status") {
            // Given
            createTestWorkflowRevision()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)

            // Create COMPLETED execution
            val completed = WorkflowExecution(
                executionId = WorkflowExecutionID.Companion.generate(),
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.COMPLETED,
                errorMessage = null,
                startedAt = Instant.now(),
                completedAt = Instant.now(),
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(completed)

            // Create FAILED execution
            val failed = WorkflowExecution(
                executionId = WorkflowExecutionID.Companion.generate(),
                revisionId = revisionId,
                inputParameters = emptyMap(),
                status = ExecutionStatus.FAILED,
                errorMessage = "Test failure",
                startedAt = Instant.now(),
                completedAt = Instant.now(),
                lastUpdatedAt = Instant.now()
            )
            repository.createExecution(failed)

            // When
            val completedExecs = repository.findByWorkflowRevision(
                revisionId = revisionId,
                status = ExecutionStatus.COMPLETED
            )
            val failedExecs = repository.findByWorkflowRevision(
                revisionId = revisionId,
                status = ExecutionStatus.FAILED
            )

            // Then
            completedExecs shouldHaveSize 1
            completedExecs[0].status shouldBe ExecutionStatus.COMPLETED
            failedExecs shouldHaveSize 1
            failedExecs[0].status shouldBe ExecutionStatus.FAILED
        }

        scenario("should support pagination") {
            // Given
            createTestWorkflowRevision()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            repeat(5) {
                val execution = WorkflowExecution(
                    executionId = WorkflowExecutionID.Companion.generate(),
                    revisionId = revisionId,
                    inputParameters = emptyMap(),
                    status = ExecutionStatus.COMPLETED,
                    errorMessage = null,
                    startedAt = Instant.now(),
                    completedAt = Instant.now(),
                    lastUpdatedAt = Instant.now()
                )
                repository.createExecution(execution)
            }

            // When
            val page1 = repository.findByWorkflowRevision(revisionId, limit = 2, offset = 0)
            val page2 = repository.findByWorkflowRevision(revisionId, limit = 2, offset = 2)

            // Then
            page1 shouldHaveSize 2
            page2 shouldHaveSize 2
        }

        scenario("should count executions by revision") {
            // Given
            createTestWorkflowRevision()
            val revisionId = WorkflowRevisionID("test-ns", "test-wf", 1)
            repeat(3) {
                val execution = WorkflowExecution(
                    executionId = WorkflowExecutionID.Companion.generate(),
                    revisionId = revisionId,
                    inputParameters = emptyMap(),
                    status = if (it == 0) ExecutionStatus.FAILED else ExecutionStatus.COMPLETED,
                    errorMessage = if (it == 0) "Failed" else null,
                    startedAt = Instant.now(),
                    completedAt = Instant.now(),
                    lastUpdatedAt = Instant.now()
                )
                repository.createExecution(execution)
            }

            // When
            val totalCount = repository.countByWorkflowRevision(revisionId)
            val failedCount = repository.countByWorkflowRevision(revisionId, ExecutionStatus.FAILED)

            // Then
            totalCount shouldBe 3
            failedCount shouldBe 1
        }
    }
})