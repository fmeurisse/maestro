package io.maestro.core.errors

import io.maestro.model.errors.MaestroException

/**
 * Thrown when workflow revision parsing fails (REQ-WF-056, REQ-WF-057, REQ-WF-058).
 *
 * This exception is thrown when YAML parsing fails or when the parsed workflow
 * revision data is invalid. It conforms to RFC 7807 Problem Details format.
 */
class WorkflowRevisionParsingException(
    message: String,
    cause: Throwable? = null
) : MaestroException(
    type = "/problems/workflow-revision-parsing-failed",
    title = "Workflow Revision Parsing Failed",
    status = 400,
    message = message,
    instance = null,
    cause = cause
)