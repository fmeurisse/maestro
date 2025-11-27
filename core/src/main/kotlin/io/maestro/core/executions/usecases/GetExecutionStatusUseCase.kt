package io.maestro.core.executions.usecases

import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Use case for retrieving the status of a workflow execution.
 *
 * This use case queries the execution record by ID and returns the complete
 * execution details including status, timestamps, input parameters, and error messages.
 */
@ApplicationScoped
class GetExecutionStatusUseCase @Inject constructor(
    private val executionRepository: IWorkflowExecutionRepository
) {

    /**
     * Get the status of a workflow execution by its ID.
     *
     * @param executionId The execution ID to query
     * @return The workflow execution details, or null if not found
     */
    fun getStatus(executionId: WorkflowExecutionID): WorkflowExecution? {
        return executionRepository.findById(executionId)
    }
}
