package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

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
class DeactivateRevisionUseCase @Inject constructor(
    private val repository: IWorkflowRevisionRepository
) {

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
     * @return The deactivated revision
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(namespace: String, id: String, version: Int): WorkflowRevision {
        logger.info { "Executing deactivation use case for $namespace/$id/$version" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Deactivate the revision (repository handles existence check)
        val deactivated = repository.deactivate(revisionId)

        logger.info { "Successfully deactivated revision: $revisionId" }
        return deactivated
    }

    /**
     * Deactivates a workflow revision using WorkflowRevisionID.
     *
     * @param revisionId The complete revision identifier
     * @return The deactivated revision
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun execute(revisionId: WorkflowRevisionID): WorkflowRevision {
        return execute(revisionId.namespace, revisionId.id, revisionId.version)
    }
}
