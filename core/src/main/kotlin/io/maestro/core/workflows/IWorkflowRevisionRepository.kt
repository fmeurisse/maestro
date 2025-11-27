package io.maestro.core.workflows

import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource

/**
 * Repository interface for workflow revision persistence.
 * Provides two sets of methods:
 * - Methods returning WorkflowRevision (without YAML source) - for most operations
 * - Methods returning WorkflowRevisionWithSource (with YAML source) - when explicitly needed
 *
 * This dual API pattern optimizes memory and network transfer for operations that don't
 * need the YAML source (e.g., listing workflows, checking active state, execution planning).
 *
 * Implementations must enforce:
 * - Uniqueness of (namespace, id, version) composite key
 * - Sequential versioning with no gaps
 * - Immutability of namespace, id, version, createdAt
 * - Active revisions cannot be updated or deleted
 */
interface IWorkflowRevisionRepository {

    // ===== Methods with YAML source (WorkflowRevisionWithSource) =====

    /**
     * Save a new workflow revision WITH YAML source.
     * Throws exception if (namespace, id, version) already exists.
     *
     * @param revision The workflow revision to save
     * @return The saved revision
     * @throws io.maestro.core.errors.WorkflowAlreadyExistsException if revision already exists
     */
    fun saveWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource

    /**
     * Update an existing workflow revision WITH YAML source.
     * Throws exception if revision doesn't exist or is active.
     *
     * @param revision The workflow revision to update
     * @return The updated revision
     * @throws io.maestro.core.errors.WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws io.maestro.core.errors.ActiveRevisionConflictException if revision is active
     */
    fun updateWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource

    /**
     * Find revision by composite ID WITH YAML source.
     * Returns null if not found.
     * Use this when caller needs the original YAML (e.g., for editing, viewing source).
     *
     * @param id The composite identifier (namespace + id + version)
     * @return The revision with source, or null if not found
     */
    fun findByIdWithSource(id: WorkflowRevisionID): WorkflowRevisionWithSource?

    // ===== Methods without YAML source (WorkflowRevision) =====

    /**
     * Find revision by composite ID WITHOUT YAML source.
     * Returns null if not found.
     * Use this for most operations where YAML source is not needed (activation, execution).
     *
     * @param id The composite identifier (namespace + id + version)
     * @return The revision without source, or null if not found
     */
    fun findById(id: WorkflowRevisionID): WorkflowRevision?

    /**
     * Find all revisions of a workflow (namespace + id) WITHOUT YAML source.
     * Returns empty list if workflow doesn't exist.
     * Sorted by version ascending.
     * Use this for listing revisions where YAML source is not displayed.
     *
     * @param workflowId The workflow identifier (namespace + id)
     * @return List of revisions, sorted by version
     */
    fun findByWorkflowId(workflowId: WorkflowID): List<WorkflowRevision>

    /**
     * Find all active revisions of a workflow WITHOUT YAML source.
     * Returns empty list if no active revisions.
     * Use this for execution planning, routing decisions.
     *
     * @param workflowId The workflow identifier (namespace + id)
     * @return List of active revisions
     */
    fun findActiveRevisions(workflowId: WorkflowID): List<WorkflowRevision>

    // ===== Utility methods (no YAML source needed) =====

    /**
     * Find maximum version number for a workflow.
     * Returns null if workflow doesn't exist.
     *
     * @param workflowId The workflow identifier (namespace + id)
     * @return Maximum version number, or null if workflow doesn't exist
     */
    fun findMaxVersion(workflowId: WorkflowID): Int?

    /**
     * Check if a workflow exists (has at least one revision).
     *
     * @param workflowId The workflow identifier (namespace + id)
     * @return True if workflow has at least one revision
     */
    fun exists(workflowId: WorkflowID): Boolean

    /**
     * Delete a specific revision.
     * Throws exception if revision is active or doesn't exist.
     *
     * @param id The composite identifier (namespace + id + version)
     * @throws io.maestro.core.errors.WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws io.maestro.core.errors.ActiveRevisionConflictException if revision is active
     */
    fun deleteById(id: WorkflowRevisionID)

    /**
     * Delete all revisions of a workflow.
     * Returns count of deleted revisions.
     *
     * @param workflowId The workflow identifier (namespace + id)
     * @return Count of deleted revisions
     */
    fun deleteByWorkflowId(workflowId: WorkflowID): Int

    /**
     * List all workflows in a namespace.
     * Returns list of WorkflowID (unique namespace + id combinations).
     *
     * @param namespace The namespace to list workflows from
     * @return List of workflow identifiers
     */
    fun listWorkflows(namespace: String): List<WorkflowID>

    /**
     * Activate a workflow revision (updates active flag and YAML source).
     * Returns the updated revision WITH YAML source.
     *
     * @param id The composite identifier (namespace + id + version)
     * @param updatedYamlSource The updated YAML source with metadata changes
     * @return The activated revision with updated YAML source
     * @throws io.maestro.core.errors.WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun activateWithSource(id: WorkflowRevisionID, updatedYamlSource: String): WorkflowRevisionWithSource

    /**
     * Deactivate a workflow revision (updates active flag and YAML source).
     * Returns the updated revision WITH YAML source.
     *
     * @param id The composite identifier (namespace + id + version)
     * @param updatedYamlSource The updated YAML source with metadata changes
     * @return The deactivated revision with updated YAML source
     * @throws io.maestro.core.errors.WorkflowRevisionNotFoundException if revision doesn't exist
     */
    fun deactivateWithSource(id: WorkflowRevisionID, updatedYamlSource: String): WorkflowRevisionWithSource
}