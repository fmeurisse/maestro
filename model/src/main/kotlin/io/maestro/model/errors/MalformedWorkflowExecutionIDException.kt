package io.maestro.model.errors

/**
 * Thrown when a Workflow execution ID string cannot be parsed from the expected format.
 * 
 * The expected format is NanoId with a length of 21 characters.
 * 
 * @property input The malformed input string that failed to parse
 * @property reason Optional detailed reason for the parsing failure
 */
class MalformedWorkflowExecutionIDException(
    val input: String,
    reason: String? = null
) : MaestroException(
    type = "/problems/malformed-workflow-execution-id",
    title = "Malformed WorkflowExecutionID",
    status = 400,
    message = reason ?: "Invalid WorkflowExecutionID format: $input (expected NanoId of length 21)",
    instance = null
)
