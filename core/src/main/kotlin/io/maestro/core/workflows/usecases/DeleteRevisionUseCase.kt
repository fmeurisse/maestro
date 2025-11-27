package io.maestro.core.workflows.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.model.WorkflowRevisionID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Use case for deleting an inactive workflow revision.
 * Implements FR-014 - Delete individual revisions.
 *
 * This use case allows deletion of inactive revisions while preventing
 * deletion of active revisions to maintain execution consistency.
 *
 * Business Rules:
 * - Only inactive revisions can be deleted (FR-014)
 * - Active revisions must be deactivated first before deletion
 * - Deletion is permanent and cannot be undone
 *
 * Clean Architecture: Business logic isolated from infrastructure concerns.
 */
@ApplicationScoped
class DeleteRevisionUseCase @Inject constructor(
    private val repository: IWorkflowRevisionRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Deletes an inactive workflow revision.
     *
     * Implements:
     * - FR-014: Delete individual revisions
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to delete
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws ActiveRevisionConflictException if revision is active
     */
    fun execute(namespace: String, id: String, version: Int) {
        logger.info { "Executing delete revision use case for $namespace/$id/$version" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Check if the revision exists
        val existing = repository.findById(revisionId)
            ?: throw WorkflowRevisionNotFoundException(revisionId)

        // Check if the revision is active (cannot delete active revisions)
        if (existing.active) {
            logger.warn { "Attempt to delete active revision: $revisionId" }
            throw ActiveRevisionConflictException(revisionId, "delete")
        }

        logger.debug { "Deleting inactive revision: $revisionId" }

        // Delete the revision
        repository.deleteById(revisionId)

        logger.info { "Successfully deleted revision: $revisionId" }
    }

    /**
     * Deletes a workflow revision using WorkflowRevisionID.
     *
     * @param revisionId The complete revision identifier
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws ActiveRevisionConflictException if revision is active
     */
    fun execute(revisionId: WorkflowRevisionID) {
        execute(revisionId.namespace, revisionId.id, revisionId.version)
    }
}
