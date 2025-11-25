package io.maestro.core.errors

import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow revision cannot be found.
 * Maps to 404 Not Found.
 *
 * This exception conforms to RFC 7807 Problem Details format.
 */
class WorkflowRevisionNotFoundException(id: WorkflowRevisionID) : MaestroException(
    type = "/problems/workflow-revision-not-found",
    title = "Workflow Revision Not Found",
    status = 404,
    message = "Workflow revision not found: $id",
    instance = null,
    cause = null
)