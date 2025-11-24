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
 * Use case for deactivating a workflow revision.
 * Implements FR-012 - Deactivate revisions.
 *
 * This use case allows revisions to be taken out of active rotation
 * for maintenance, rollback, or deprecation scenarios.
 *
 * Clean Architecture: Business logic isolated from infrastructure concerns.
 */
@ApplicationScoped
class DeactivateRevisionUseCase constructor(
    private val repository: IWorkflowRevisionRepository,
    private val clock: Clock
) {

    @Inject
    constructor(repository: IWorkflowRevisionRepository): this(repository, Clock.systemUTC())

    private val logger = KotlinLogging.logger {}

    /**
     * Deactivates a workflow revision.
     *
     * Implements:
     * - FR-012: Mark revision as inactive
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to deactivate
     * @return The deactivated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(namespace: String, id: String, version: Int): WorkflowRevisionWithSource {
        logger.info { "Executing deactivation use case for $namespace/$id/$version" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Get existing revision with source
        val existing = repository.findByIdWithSource(revisionId)
            ?: throw WorkflowRevisionNotFoundException(revisionId)

        // Update YAML source with new updatedAt timestamp
        val now = Instant.now(clock)
        logger.debug { "Updating YAML source with metadata" }
        val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, now)

        // Deactivate the revision with updated YAML source
        val deactivated = repository.deactivateWithSource(revisionId, updatedYaml)

        logger.info { "Successfully deactivated revision: $revisionId" }
        return deactivated
    }

    /**
     * Deactivates a workflow revision using WorkflowRevisionID.
     *
     * @param revisionId The complete revision identifier
     * @return The deactivated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(revisionId: WorkflowRevisionID): WorkflowRevisionWithSource {
        return execute(revisionId.namespace, revisionId.id, revisionId.version)
    }
}
