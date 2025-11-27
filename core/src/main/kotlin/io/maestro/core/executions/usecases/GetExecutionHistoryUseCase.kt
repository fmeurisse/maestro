package io.maestro.core.executions.usecases

import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.WorkflowExecution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Result data class for execution history queries.
 *
 * Contains the list of executions and total count for pagination support.
 */
data class ExecutionHistoryResult(
    val executions: List<WorkflowExecution>,
    val totalCount: Long
)

/**
 * Use case for retrieving execution history for a workflow.
 *
 * This use case queries executions for a workflow (all versions) with support for:
 * - Version filtering (optional, null = all versions)
 * - Status filtering (optional)
 * - Pagination (limit/offset)
 * - Sorting by most recent first (startedAt descending)
 *
 * Implements User Story 4 (US4): View Execution History.
 */
@ApplicationScoped
class GetExecutionHistoryUseCase @Inject constructor(
    private val executionRepository: IWorkflowExecutionRepository
) {

    /**
     * Get execution history for a workflow.
     *
     * @param namespace The workflow namespace
     * @param workflowId The workflow identifier
     * @param version Optional version filter (null = all versions)
     * @param status Optional status filter (null = all statuses)
     * @param limit Maximum number of results to return (default: 20, max: 100)
     * @param offset Number of results to skip for pagination (default: 0)
     * @return ExecutionHistoryResult containing executions and total count
     */
    fun getHistory(
        namespace: String,
        workflowId: String,
        version: Int? = null,
        status: ExecutionStatus? = null,
        limit: Int = 20,
        offset: Int = 0
    ): ExecutionHistoryResult {
        // Validate limit
        require(limit > 0) { "Limit must be positive, got: $limit" }
        require(limit <= 100) { "Limit must be <= 100, got: $limit" }
        require(offset >= 0) { "Offset must be >= 0, got: $offset" }

        // Query executions with filtering and pagination
        val executions = executionRepository.findByWorkflow(
            namespace = namespace,
            workflowId = workflowId,
            version = version,
            status = status,
            limit = limit,
            offset = offset
        )

        // Get total count for pagination metadata
        val totalCount = executionRepository.countByWorkflow(
            namespace = namespace,
            workflowId = workflowId,
            version = version,
            status = status
        )

        return ExecutionHistoryResult(
            executions = executions,
            totalCount = totalCount
        )
    }
}
