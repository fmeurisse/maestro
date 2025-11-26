package io.maestro.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.maestro.model.parameters.ParameterDefinition
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
 * @property parameters List of parameter definitions for workflow input (optional)
 * @property steps Parsed workflow step tree
 * @property active Whether this revision is active for execution
 * @property createdAt UTC timestamp when revision was created (immutable)
 * @property updatedAt UTC timestamp of the last modification (activation, deactivation, update)
 */
data class WorkflowRevision(
    override val namespace: String,
    override val id: String,
    override val version: Int = 0,
    val name: String,
    val description: String,
    @param:JsonProperty(required = false)
    val parameters: List<ParameterDefinition> = emptyList(),
    val steps: List<Step>,
    val active: Boolean = false,
    @param:JsonProperty(required = false)
    val createdAt: Instant? = null,
    @param:JsonProperty(required = false)
    val updatedAt: Instant? = null
): IWorkflowRevisionID {

    fun validate(): WorkflowRevision = this.apply {
        validate(namespace, id, version, name, description)
    }

    fun withVersion(version: Int): WorkflowRevision = copy(version = version)

    fun toWorkflowRevisionID(): WorkflowRevisionID = WorkflowRevisionID(namespace, id, version)

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
         * @throws InvalidWorkflowRevisionException if any validation rule is violated
         */
        @Throws(InvalidWorkflowRevisionException::class)
        fun validateAndCreate(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            parameters: List<ParameterDefinition> = emptyList(),
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevision {
            validate(namespace, id, version, name, description)
            return create(
                namespace, id, version, name, description,
                parameters, steps, active, createdAt, updatedAt
            )
        }

        fun create(
            namespace: String,
            id: String,
            version: Int = 0,
            name: String,
            description: String,
            parameters: List<ParameterDefinition> = emptyList(),
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevision = WorkflowRevision(
            namespace, id, version, name, description,
            parameters, steps, active, createdAt, updatedAt
        )

        fun validate(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String
        ) {
            if (namespace.isBlank()) {
                throw InvalidWorkflowRevisionException("Namespace must not be blank")
            }
            if (namespace.length > MAX_NAMESPACE_LENGTH) {
                throw InvalidWorkflowRevisionException("Namespace must not exceed $MAX_NAMESPACE_LENGTH characters")
            }
            if (!namespace.matches(NAMESPACE_REGEX)) {
                throw InvalidWorkflowRevisionException(
                    "Namespace must contain only alphanumeric characters, hyphens, and underscores"
                )
            }

            if (id.isBlank()) {
                throw InvalidWorkflowRevisionException("ID must not be blank")
            }
            if (id.length > MAX_ID_LENGTH) {
                throw InvalidWorkflowRevisionException("ID must not exceed $MAX_ID_LENGTH characters")
            }
            if (!id.matches(ID_REGEX)) {
                throw InvalidWorkflowRevisionException(
                    "ID must contain only alphanumeric characters, hyphens, and underscores"
                )
            }

            if (version <= 0) {
                throw InvalidWorkflowRevisionException("Version must be positive")
            }

            if (name.isBlank()) {
                throw InvalidWorkflowRevisionException("Name must not be blank")
            }
            if (name.length > MAX_NAME_LENGTH) {
                throw InvalidWorkflowRevisionException("Name must not exceed $MAX_NAME_LENGTH characters")
            }

            if (description.length > MAX_DESCRIPTION_LENGTH) {
                throw InvalidWorkflowRevisionException(
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
