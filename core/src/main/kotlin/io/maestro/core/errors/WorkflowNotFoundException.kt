package io.maestro.core.errors

import io.maestro.model.WorkflowID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow cannot be found.
 * Maps to 404 Not Found.
 *
 * This exception conforms to RFC 7807 Problem Details format.
 */
class WorkflowNotFoundException(workflowId: WorkflowID) : MaestroException(
    type = "/problems/workflow-not-found",
    title = "Workflow Not Found",
    status = 404,
    message = "Workflow not found: $workflowId",
    instance = null,
    cause = null
)
