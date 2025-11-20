package io.maestro.model

import io.maestro.model.exception.InvalidWorkflowRevisionException

/**
 * Standalone implementation of workflow revision ID for queries
 * @throws InvalidWorkflowRevisionException if validation fails
 */
data class WorkflowRevisionID(
    override val namespace: String,
    override val id: String,
    override val version: Long
) : IWorkflowRevisionID {

    init {
        val errors = mutableListOf<String>()

        if (namespace.isBlank()) errors.add("Namespace must not be blank")
        if (id.isBlank()) errors.add("ID must not be blank")
        if (version <= 0) errors.add("Version must be positive")

        if (errors.isNotEmpty()) {
            throw InvalidWorkflowRevisionException(
                message = "Invalid workflow revision ID: ${errors.joinToString(", ")}",
                field = when {
                    namespace.isBlank() -> "namespace"
                    id.isBlank() -> "id"
                    else -> "version"
                },
                rejectedValue = when {
                    namespace.isBlank() -> namespace
                    id.isBlank() -> id
                    else -> version
                }
            )
        }
    }
}