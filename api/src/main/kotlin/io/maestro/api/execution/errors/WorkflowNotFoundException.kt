package io.maestro.api.execution.errors

import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow revision is not found during execution request.
 * Maps to 404 Not Found with RFC 7807 problem+json format.
 */
class WorkflowNotFoundException(
    val revisionId: WorkflowRevisionID,
    message: String = buildMessage(revisionId)
) : MaestroException(
    type = "/problems/workflow-not-found",
    title = "Workflow Not Found",
    status = 404,
    message = message,
    instance = null
) {
    companion object {
        private fun buildMessage(revisionId: WorkflowRevisionID): String =
            "Workflow revision not found: ${revisionId.namespace}/${revisionId.id}/v${revisionId.version}. " +
            "Ensure the workflow exists and the version number is correct."
    }
}
