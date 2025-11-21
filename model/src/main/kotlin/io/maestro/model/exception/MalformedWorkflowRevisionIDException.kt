package io.maestro.model.exception

/**
 * Thrown when a WorkflowRevisionID string cannot be parsed from the expected format.
 * 
 * The expected format is "namespace:id:version" where:
 * - namespace and id are non-blank strings
 * - version is a positive integer
 * 
 * @property input The malformed input string that failed to parse
 * @property reason Optional detailed reason for the parsing failure
 */
class MalformedWorkflowRevisionIDException(
    val input: String,
    reason: String? = null
) : MaestroException(
    type = "/problems/malformed-workflow-revision-id",
    title = "Malformed WorkflowRevisionID",
    status = 400,
    message = reason ?: "Invalid WorkflowRevisionID format: $input (expected namespace:id:version)",
    instance = null
)
