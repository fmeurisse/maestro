package io.maestro.core.workflows.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.model.WorkflowID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Use case for deleting all revisions of a workflow.
 * Implements FR-015 - Delete entire workflows.
 *
 * This use case allows deletion of all revisions of a workflow in one operation.
 * All revisions must be deactivated before the workflow can be deleted.
 *
 * Business Rules:
 * - All revisions must be inactive before deletion (FR-015)
 * - Throws exception if any active revisions exist
 * - Returns count of deleted revisions
 * - Deletion is permanent and cannot be undone
 *
 * Clean Architecture: Business logic isolated from infrastructure concerns.
 */
@ApplicationScoped
class DeleteWorkflowUseCase @Inject constructor(
    private val repository: IWorkflowRevisionRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Deletes all revisions of a workflow.
     *
     * Implements:
     * - FR-015: Delete entire workflows
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @return Count of deleted revisions
     * @throws ActiveRevisionConflictException if any active revisions exist
     */
    fun execute(namespace: String, id: String): Int {
        logger.info { "Executing delete workflow use case for $namespace/$id" }

        val workflowId = WorkflowID(namespace, id)

        // Check if there are any active revisions
        val activeRevisions = repository.findActiveRevisions(workflowId)
        if (activeRevisions.isNotEmpty()) {
            logger.warn { "Attempt to delete workflow with active revisions: $workflowId (${activeRevisions.size} active)" }
            // Throw exception for the first active revision found
            val firstActive = activeRevisions.first()
            throw ActiveRevisionConflictException(
                firstActive.revisionId(),
                "delete workflow - deactivate all revisions first"
            )
        }

        logger.debug { "Deleting all revisions for workflow: $workflowId" }

        // Delete all revisions of the workflow
        val deletedCount = repository.deleteByWorkflowId(workflowId)

        logger.info { "Successfully deleted $deletedCount revisions for workflow: $workflowId" }
        return deletedCount
    }

    /**
     * Deletes all revisions of a workflow using WorkflowID.
     *
     * @param workflowId The workflow identifier
     * @return Count of deleted revisions
     */
    fun execute(workflowId: WorkflowID): Int {
        return execute(workflowId.namespace, workflowId.id)
    }
}
