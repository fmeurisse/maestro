package io.maestro.model.exception

/**
 * Base exception for model validation errors.
 * Implements RFC 7807 Problem Details structure.
 *
 * @property message Human-readable error message
 * @property field The field that failed validation
 * @property rejectedValue The value that was rejected
 * @property type URI reference that identifies the problem type (RFC 7807)
 */
sealed class ModelValidationException(
    override val message: String,
    open val field: String,
    open val rejectedValue: Any?,
    open val type: String = "about:blank"
) : RuntimeException(message)

/**
 * Thrown when a WorkflowRevision or WorkflowRevisionID fails validation.
 * Maps to HTTP 400 Bad Request with RFC 7807 Problem Details.
 *
 * @property message Human-readable description of the validation failure
 * @property field The specific field that failed validation
 * @property rejectedValue The value that was rejected during validation
 */
class InvalidWorkflowRevisionException(
    override val message: String,
    override val field: String,
    override val rejectedValue: Any?,
    override val type: String = "/problems/invalid-workflow-revision"
) : ModelValidationException(message, field, rejectedValue, type)
