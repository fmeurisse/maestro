package io.maestro.core.errors

import io.maestro.model.IWorkflowID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow cannot be found.
 * Maps to 404 Not Found.
 *
 * This exception conforms to RFC 7807 Problem Details format.
 */
class WorkflowNotFoundException(workflowId: IWorkflowID) : MaestroException(
    type = "/problems/workflow-not-found",
    title = "Workflow Not Found",
    status = 404,
    message = "Workflow not found: ${workflowId.namespace}::${workflowId.id}",
    instance = null,
    cause = null
)