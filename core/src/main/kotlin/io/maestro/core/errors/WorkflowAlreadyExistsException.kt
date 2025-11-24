package io.maestro.core.errors

import io.maestro.model.IWorkflowID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when attempting to create a workflow that already exists.
 * Maps to 409 Conflict.
 * 
 * This exception conforms to RFC 7807 Problem Details format.
 */
class WorkflowAlreadyExistsException(id: IWorkflowID) : MaestroException(
    type = "/problems/workflow-already-exists",
    title = "Workflow Already Exists",
    status = 409,
    message = "Workflow already exists: $id",
    instance = null,
    cause = null
)
