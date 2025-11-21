package io.maestro.model.exception

/**
 * Thrown when a workflow revision fails domain validation.
 * 
 * This exception indicates a violation of workflow revision domain invariants:
 * - Invalid namespace or id format
 * - Invalid version number
 * - Blank required fields
 * - Fields exceeding max length
 * - Missing YAML source
 * 
 * @property message Human-readable validation error message
 */
class InvalidWorkflowRevision(
    message: String,
    cause: Throwable? = null
) : MaestroException(
    type = "/problems/invalid-workflow-revision",
    title = "Invalid Workflow Revision",
    status = 400,
    message = message,
    instance = null,
    cause = cause
)
