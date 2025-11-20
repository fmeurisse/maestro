package io.maestro.core.repository

import io.maestro.model.IWorkflowRevisionID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionWithSource

/**
 * Repository interface for workflow revision persistence operations.
 */
interface IWorkflowRevisionRepository {

    /**
     * Finds a workflow revision by its composite ID (without YAML source).
     * Use this for execution and operations that don't need the YAML source.
     *
     * @param id Composite identifier (namespace, id, version)
     * @return The workflow revision if found, null otherwise
     */
    fun findById(id: IWorkflowRevisionID): WorkflowRevision?

    /**
     * Finds a workflow revision WITH its YAML source by its composite ID.
     * Use this for API responses, exports, or auditing when the YAML source is needed.
     *
     * @param id Composite identifier (namespace, id, version)
     * @return The workflow revision with source if found, null otherwise
     */
    fun findByIdWithSource(id: IWorkflowRevisionID): WorkflowRevisionWithSource?

    /**
     * Checks if a workflow with the given namespace and id already exists.
     * This is specifically for checking version 1 existence during creation.
     *
     * @param namespace Workflow namespace
     * @param id Workflow ID
     * @return true if any version exists for this workflow
     */
    fun existsByWorkflowId(namespace: String, id: String): Boolean

    /**
     * Saves a workflow revision WITH its YAML source (create or update).
     *
     * @param workflowRevision The revision to save
     * @param yaml The original YAML source
     * @return The saved revision (without source - use findByIdWithSource if needed)
     * @throws DataIntegrityViolationException if uniqueness constraints are violated
     */
    fun save(workflowRevision: WorkflowRevision, yaml: String): WorkflowRevision

    /**
     * Finds all workflow revisions (without YAML source, primarily for testing/admin purposes).
     *
     * @return List of all revisions
     */
    fun findAll(): List<WorkflowRevision>

    /**
     * Deletes a workflow revision by its composite ID.
     *
     * @param id Composite identifier (namespace, id, version)
     */
    fun deleteById(id: IWorkflowRevisionID)
}