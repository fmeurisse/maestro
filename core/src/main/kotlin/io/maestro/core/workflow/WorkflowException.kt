package io.maestro.core.workflow

import io.maestro.model.WorkflowRevisionID
import java.net.URI

/**
 * Base sealed class for all workflow-related domain exceptions.
 * All workflow exceptions map to RFC 7807 JSON Problem responses.
 *
 * @property message Human-readable error message
 * @property cause Optional underlying cause
 * @property problemType RFC 7807 problem type URI
 * @property status HTTP status code
 */
sealed class WorkflowException(
    message: String,
    cause: Throwable? = null,
    val problemType: URI,
    val status: Int
) : RuntimeException(message, cause)

/**
 * Thrown when a workflow revision cannot be found.
 * Maps to 404 Not Found.
 */
class WorkflowNotFoundException(id: WorkflowRevisionID) :
    WorkflowException(
        message = "Workflow revision not found: $id",
        problemType = URI.create("https://maestro.io/problems/workflow-not-found"),
        status = 404
    )

/**
 * Thrown when attempting to create a workflow that already exists.
 * Maps to 409 Conflict.
 */
class WorkflowAlreadyExistsException(id: WorkflowRevisionID) :
    WorkflowException(
        message = "Workflow revision already exists: $id",
        problemType = URI.create("https://maestro.io/problems/workflow-exists"),
        status = 409
    )

/**
 * Thrown when attempting to modify or delete an active revision.
 * Active revisions must be deactivated before they can be updated or deleted.
 * Maps to 409 Conflict.
 *
 * @property operation The operation that was attempted (e.g., "update", "delete")
 */
class ActiveRevisionConflictException(id: WorkflowRevisionID, operation: String) :
    WorkflowException(
        message = "Cannot $operation active revision: $id. Deactivate it first.",
        problemType = URI.create("https://maestro.io/problems/active-revision-conflict"),
        status = 409
    )

/**
 * Thrown when YAML parsing fails.
 * Maps to 400 Bad Request.
 */
class InvalidYamlException(message: String, cause: Throwable? = null) :
    WorkflowException(
        message = "Invalid YAML: $message",
        cause = cause,
        problemType = URI.create("https://maestro.io/problems/invalid-yaml"),
        status = 400
    )

/**
 * Thrown when a workflow step is invalid or unknown.
 * Maps to 400 Bad Request.
 */
class InvalidStepException(message: String) :
    WorkflowException(
        message = "Invalid workflow step: $message",
        problemType = URI.create("https://maestro.io/problems/invalid-step"),
        status = 400
    )

/**
 * Thrown when field validation fails (from model ValidationException).
 * Maps to 400 Bad Request.
 */
class ValidationException(message: String) :
    WorkflowException(
        message = "Validation failed: $message",
        problemType = URI.create("https://maestro.io/problems/validation-error"),
        status = 400
    )
