package io.maestro.model.errors

/**
 * Thrown when a WorkflowID string cannot be parsed from the expected format.
 * 
 * The expected format is "namespace:id" where both namespace and id are non-blank strings.
 * 
 * @property input The malformed input string that failed to parse
 * @property reason Optional detailed reason for the parsing failure
 */
class MalformedWorkflowIDException(
    val input: String,
    reason: String? = null
) : MaestroException(
    type = "/problems/malformed-workflow-id",
    title = "Malformed WorkflowID",
    status = 400,
    message = reason ?: "Invalid WorkflowID format: $input (expected namespace:id)",
    instance = null
)
