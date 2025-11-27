package io.maestro.model

import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.maestro.model.parameters.ParameterDefinition
import io.maestro.model.steps.Step
import java.time.Instant

/**
 * Workflow revision WITH original YAML source.
 * Use this only when YAML source is explicitly needed: create, update, GET with source.
 *
 * This composition pattern allows:
 * - Repository to return WorkflowRevision for most queries (lighter weight)
 * - Repository to return WorkflowRevisionWithSource only when yamlSource is requested
 * - API layer to choose which representation to use based on endpoint needs
 *
 * @property revision The core workflow revision entity
 * @property yamlSource Original YAML definition with preserved formatting and comments
 */
data class WorkflowRevisionWithSource(
    val revision: WorkflowRevision,
    val yamlSource: String
): IWorkflowRevisionID {
    // Convenience accessors that delegate to revision
    override val namespace: String get() = revision.namespace
    override val id: String get() = revision.id
    override val version: Int get() = revision.version
    val name: String get() = revision.name
    val description: String get() = revision.description
    val parameters: List<ParameterDefinition> get() = revision.parameters
    val steps: List<Step> get() = revision.steps
    val active: Boolean get() = revision.active
    val createdAt: Instant? get() = revision.createdAt
    val updatedAt: Instant? get() = revision.updatedAt


    fun toWorkflowRevisionID(): WorkflowRevisionID = revision.toWorkflowRevisionID()

    companion object {
        /**
         * Factory method with domain validation that throws domain exceptions.
         * Validates both revision data and YAML source.
         *
         * @throws InvalidWorkflowRevisionException if any validation rule is violated
         */
        @Throws(InvalidWorkflowRevisionException::class)
        fun create(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            yamlSource: String,
            parameters: List<ParameterDefinition> = emptyList(),
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevisionWithSource {
            if (yamlSource.isBlank()) {
                throw InvalidWorkflowRevisionException("YAML source must not be blank")
            }
            val revision = WorkflowRevision.validateAndCreate(
                namespace, id, version, name, description,
                parameters, steps, active, createdAt, updatedAt
            )
            return WorkflowRevisionWithSource(revision, yamlSource)
        }

        /**
         * Create from existing WorkflowRevision with YAML source
         */
        @Throws(InvalidWorkflowRevisionException::class)
        fun fromRevision(revision: WorkflowRevision, yamlSource: String): WorkflowRevisionWithSource {
            if (yamlSource.isBlank()) {
                throw InvalidWorkflowRevisionException("YAML source must not be blank")
            }
            return WorkflowRevisionWithSource(revision, yamlSource)
        }
    }

    /**
     * Convert to core WorkflowRevision (dropping YAML source)
     */
    fun toRevision(): WorkflowRevision = revision

    /**
     * Create a copy with updated revision and timestamp
     */
    fun withUpdatedTimestamp(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.withUpdatedTimestamp(now))

    /**
     * Activate this revision with source
     */
    fun activate(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.activate(now))

    /**
     * Deactivate this revision with source
     */
    fun deactivate(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.deactivate(now))

    /**
     * Update YAML source and steps (for inactive revision updates)
     */
    fun updateContent(newYamlSource: String, newSteps: List<Step>, newDescription: String? = null): WorkflowRevisionWithSource {
        if (newYamlSource.isBlank()) {
            throw InvalidWorkflowRevisionException("YAML source must not be blank")
        }
        val updatedRevision = revision.copy(
            description = newDescription ?: revision.description,
            steps = newSteps,
            updatedAt = Instant.now()
        )
        return WorkflowRevisionWithSource(updatedRevision, newYamlSource)
    }

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
