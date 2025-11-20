package io.maestro.model

import io.maestro.model.exception.InvalidWorkflowRevisionException
import io.maestro.model.steps.Step
import java.time.Instant

/**
 * Represents a specific version of a workflow definition without the YAML source.
 * This is the core domain model used for workflow execution and most operations.
 *
 * @property namespace Logical namespace for workflow isolation (e.g., "production", "development")
 * @property id Unique identifier for the workflow within its namespace
 * @property version Sequential version number (starts at 1)
 * @property name Human-readable name for the workflow
 * @property description Detailed description of what this workflow does
 * @property active Whether this revision is currently active and available for execution
 * @property rootStep Root step definition for the workflow (typically a Sequence or other orchestration step)
 * @property createdAt Timestamp when this revision was created (UTC, immutable)
 * @property updatedAt Timestamp when this revision was last modified (UTC)
 * @throws InvalidWorkflowRevisionException if validation fails
 */
data class WorkflowRevision(
    override val namespace: String,
    override val id: String,
    override val version: Long,
    val name: String,
    val description: String,
    val active: Boolean = false,
    val rootStep: Step,
    val createdAt: Instant,
    val updatedAt: Instant
) : IWorkflowRevisionID {

    init {
        val errors = mutableListOf<String>()

        if (namespace.isBlank()) errors.add("Namespace must not be blank")
        if (id.isBlank()) errors.add("ID must not be blank")
        if (version <= 0) errors.add("Version must be positive")
        if (name.isBlank()) errors.add("Name must not be blank")
        if (description.isBlank()) errors.add("Description must not be blank")

        if (errors.isNotEmpty()) {
            throw InvalidWorkflowRevisionException(
                message = "Invalid workflow revision: ${errors.joinToString(", ")}",
                field = when {
                    namespace.isBlank() -> "namespace"
                    id.isBlank() -> "id"
                    version <= 0 -> "version"
                    name.isBlank() -> "name"
                    else -> "description"
                },
                rejectedValue = when {
                    namespace.isBlank() -> namespace
                    id.isBlank() -> id
                    version <= 0 -> version
                    name.isBlank() -> name
                    else -> description
                }
            )
        }
    }

    /**
     * Creates a copy of this revision with updated timestamp
     */
    fun withUpdatedTimestamp(timestamp: Instant = Instant.now()): WorkflowRevision {
        return copy(updatedAt = timestamp)
    }

    /**
     * Creates a copy of this revision with active state changed
     */
    fun withActiveState(newActiveState: Boolean, timestamp: Instant = Instant.now()): WorkflowRevision {
        return copy(active = newActiveState, updatedAt = timestamp)
    }
}

/**
 * Represents a workflow revision WITH its original YAML source.
 * Use this class only when the YAML source is needed (e.g., for API responses, exports, auditing).
 * For execution and most operations, use WorkflowRevision instead.
 *
 * @property revision The core workflow revision data
 * @property yaml Original YAML definition (preserves formatting and comments)
 * @throws InvalidWorkflowRevisionException if validation fails
 */
data class WorkflowRevisionWithSource(
    val revision: WorkflowRevision,
    val yaml: String
) {
    init {
        if (yaml.isBlank()) {
            throw InvalidWorkflowRevisionException(
                message = "YAML definition must not be blank",
                field = "yaml",
                rejectedValue = yaml
            )
        }
    }

    // Delegate common properties for convenience
    val namespace: String get() = revision.namespace
    val id: String get() = revision.id
    val version: Long get() = revision.version
    val name: String get() = revision.name
    val description: String get() = revision.description
    val active: Boolean get() = revision.active
    val rootStep: Step get() = revision.rootStep
    val createdAt: Instant get() = revision.createdAt
    val updatedAt: Instant get() = revision.updatedAt
}