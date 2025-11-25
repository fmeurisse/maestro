package io.maestro.core.errors

import io.maestro.model.execution.WorkflowExecutionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when a workflow execution cannot be found.
 * Maps to 404 Not Found.
 *
 * This exception conforms to RFC 7807 Problem Details format.
 */
class ExecutionNotFoundException(executionId: WorkflowExecutionID) : MaestroException(
    type = "/problems/execution-not-found",
    title = "Workflow Execution Not Found",
    status = 404,
    message = "Workflow execution not found: $executionId",
    instance = null,
    cause = null
)
