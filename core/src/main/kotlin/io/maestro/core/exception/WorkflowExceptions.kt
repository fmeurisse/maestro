package io.maestro.core.exception

/**
 * Base exception for workflow-related errors
 */
sealed class WorkflowException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Thrown when attempting to create a workflow that already exists (REQ-WF-004)
 */
class WorkflowAlreadyExistsException(message: String) : WorkflowException(message)

/**
 * Thrown when a requested workflow is not found
 */
class WorkflowNotFoundException(message: String) : WorkflowException(message)

