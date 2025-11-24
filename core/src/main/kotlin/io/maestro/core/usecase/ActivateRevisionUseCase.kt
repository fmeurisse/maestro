package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.Instant

/**
 * Use case for activating a workflow revision.
 * Implements FR-009, FR-011 - Activate revisions with multi-active support.
 *
 * This use case supports multiple active revisions for the same workflow,
 * allowing A/B testing, gradual rollouts, and feature toggling scenarios.
 *
 * Clean Architecture: Business logic isolated from infrastructure concerns.
 */
@ApplicationScoped
class ActivateRevisionUseCase constructor(
    private val repository: IWorkflowRevisionRepository,
    private val clock: Clock
) {

    @Inject
    constructor(repository: IWorkflowRevisionRepository): this(repository, Clock.systemUTC())

    private val logger = KotlinLogging.logger {}

    /**
     * Activates a workflow revision.
     *
     * Implements:
     * - FR-009: Mark revision as active
     * - FR-011: Support multiple active revisions simultaneously
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to activate
     * @return The activated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(namespace: String, id: String, version: Int): WorkflowRevisionWithSource {
        logger.info { "Executing activation use case for $namespace/$id/$version" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Get existing revision with source
        val existing = repository.findByIdWithSource(revisionId)
            ?: throw WorkflowRevisionNotFoundException(revisionId)

        // Update YAML source with new updatedAt timestamp
        val now = Instant.now(clock)
        logger.debug { "Updating YAML source with metadata" }
        val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, now)

        // Activate the revision with updated YAML source
        val activated = repository.activateWithSource(revisionId, updatedYaml)

        logger.info { "Successfully activated revision: $revisionId" }
        return activated
    }

    /**
     * Activates a workflow revision using WorkflowRevisionID.
     *
     * @param revisionId The complete revision identifier
     * @return The activated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(revisionId: WorkflowRevisionID): WorkflowRevisionWithSource {
        return execute(revisionId.namespace, revisionId.id, revisionId.version)
    }
}
