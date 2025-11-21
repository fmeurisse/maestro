package io.maestro.model

import io.maestro.model.exception.InvalidWorkflowRevision
import io.maestro.model.steps.Step
import java.time.Instant

/**
 * Core workflow revision entity WITHOUT YAML source.
 * Use this for most operations: queries, activation, execution planning.
 *
 * This entity excludes the yamlSource field to reduce memory overhead for operations
 * that don't need the original YAML (e.g., listing workflows, checking active state,
 * execution planning). For operations requiring YAML source, use WorkflowRevisionWithSource.
 *
 * @property namespace Logical isolation boundary (e.g., "production", "staging")
 * @property id Workflow identifier within namespace
 * @property version Sequential version number (1, 2, 3...)
 * @property name Human-readable workflow name
 * @property description Workflow purpose and behavior description (max 1000 chars)
 * @property steps Parsed workflow step tree
 * @property active Whether this revision is active for execution
 * @property createdAt UTC timestamp when revision was created (immutable)
 * @property updatedAt UTC timestamp of the last modification (activation, deactivation, update)
 */
data class WorkflowRevision(
    override val namespace: String,
    override val id: String,
    override val version: Int,
    val name: String,
    val description: String,
    val steps: List<Step>,
    val active: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
): IWorkflowRevisionID {
    companion object {
        private val NAMESPACE_REGEX = Regex("^[a-zA-Z0-9_-]+$")
        private val ID_REGEX = Regex("^[a-zA-Z0-9_-]+$")
        private const val MAX_NAMESPACE_LENGTH = 100
        private const val MAX_ID_LENGTH = 100
        private const val MAX_NAME_LENGTH = 255
        private const val MAX_DESCRIPTION_LENGTH = 1000

        /**
         * Factory method with domain validation that throws domain exceptions.
         * Use this instead of constructor to get proper domain exception handling.
         *
         * @throws InvalidWorkflowRevision if any validation rule is violated
         */
        @Throws(InvalidWorkflowRevision::class)
        fun create(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevision {
            validate(namespace, id, version, name, description)
            return WorkflowRevision(
                namespace, id, version, name, description,
                steps, active, createdAt, updatedAt
            )
        }

        private fun validate(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String
        ) {
            if (namespace.isBlank()) {
                throw InvalidWorkflowRevision("Namespace must not be blank")
            }
            if (namespace.length > MAX_NAMESPACE_LENGTH) {
                throw InvalidWorkflowRevision("Namespace must not exceed $MAX_NAMESPACE_LENGTH characters")
            }
            if (!namespace.matches(NAMESPACE_REGEX)) {
                throw InvalidWorkflowRevision(
                    "Namespace must contain only alphanumeric characters, hyphens, and underscores"
                )
            }

            if (id.isBlank()) {
                throw InvalidWorkflowRevision("ID must not be blank")
            }
            if (id.length > MAX_ID_LENGTH) {
                throw InvalidWorkflowRevision("ID must not exceed $MAX_ID_LENGTH characters")
            }
            if (!id.matches(ID_REGEX)) {
                throw InvalidWorkflowRevision(
                    "ID must contain only alphanumeric characters, hyphens, and underscores"
                )
            }

            if (version <= 0) {
                throw InvalidWorkflowRevision("Version must be positive")
            }

            if (name.isBlank()) {
                throw InvalidWorkflowRevision("Name must not be blank")
            }
            if (name.length > MAX_NAME_LENGTH) {
                throw InvalidWorkflowRevision("Name must not exceed $MAX_NAME_LENGTH characters")
            }

            if (description.length > MAX_DESCRIPTION_LENGTH) {
                throw InvalidWorkflowRevision(
                    "Description must not exceed $MAX_DESCRIPTION_LENGTH characters"
                )
            }
        }
    }

    /**
     * Create a copy with updated timestamp (for activation/deactivation/update operations)
     */
    fun withUpdatedTimestamp(now: Instant = Instant.now()): WorkflowRevision =
        copy(updatedAt = now)

    /**
     * Activate this revision
     */
    fun activate(now: Instant = Instant.now()): WorkflowRevision =
        copy(active = true, updatedAt = now)

    /**
     * Deactivate this revision
     */
    fun deactivate(now: Instant = Instant.now()): WorkflowRevision =
        copy(active = false, updatedAt = now)

    /**
     * Get the composite identifier for this revision
     */
    fun revisionId(): WorkflowRevisionID =
        WorkflowRevisionID(namespace, id, version)

    /**
     * Get the workflow identifier (without version)
     */
    fun workflowId(): WorkflowID =
        WorkflowID(namespace, id)
}
