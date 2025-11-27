package io.maestro.api.execution.errors

import io.maestro.model.execution.WorkflowExecutionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow execution is not found when querying status.
 * Maps to 404 Not Found with RFC 7807 problem+json format.
 */
class ExecutionNotFoundException(
    val executionId: WorkflowExecutionID,
    message: String = buildMessage(executionId)
) : MaestroException(
    type = "/problems/execution-not-found",
    title = "Execution Not Found",
    status = 404,
    message = message,
    instance = null
) {
    companion object {
        private fun buildMessage(executionId: WorkflowExecutionID): String =
            "Workflow execution not found: ${executionId}. " +
            "Verify the execution ID is correct or check if the execution has been archived."
    }
}
