package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.OptimisticLockException
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
     * Deactivates a workflow revision with optimistic locking.
     *
     * Implements:
     * - FR-012: Mark revision as inactive
     * - T099: Optimistic locking using updatedAt field
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to deactivate
     * @param currentUpdatedAt The expected current updatedAt timestamp for optimistic locking
     * @return The deactivated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws OptimisticLockException if the revision has been modified by another user
     */
    fun execute(namespace: String, id: String, version: Int, currentUpdatedAt: Instant): WorkflowRevisionWithSource {
        logger.info { "Executing deactivation use case for $namespace/$id/$version with optimistic locking" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Get existing revision with source
        val existing = repository.findByIdWithSource(revisionId)
            ?: throw WorkflowRevisionNotFoundException(revisionId)

        // Optimistic lock check: validate updatedAt matches
        if (existing.revision.updatedAt != currentUpdatedAt) {
            logger.warn { "Optimistic lock conflict detected for deactivation of $revisionId" }
            throw OptimisticLockException(revisionId, currentUpdatedAt, existing.revision.updatedAt!!)
        }

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
     * Deactivates a workflow revision using WorkflowRevisionID with optimistic locking.
     *
     * @param revisionId The complete revision identifier
     * @param currentUpdatedAt The expected current updatedAt timestamp for optimistic locking
     * @return The deactivated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws OptimisticLockException if the revision has been modified by another user
     */
    fun execute(revisionId: WorkflowRevisionID, currentUpdatedAt: Instant): WorkflowRevisionWithSource {
        return execute(revisionId.namespace, revisionId.id, revisionId.version, currentUpdatedAt)
    }
}
