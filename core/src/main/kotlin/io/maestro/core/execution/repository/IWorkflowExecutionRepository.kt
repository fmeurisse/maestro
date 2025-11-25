package io.maestro.core.execution.repository

import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.ExecutionStepResult
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID

/**
 * Repository interface for workflow execution persistence.
 * 
 * Provides methods for:
 * - Creating and updating execution records
 * - Persisting step results (per-step commits for crash recovery)
 * - Querying execution status and history
 * 
 * Implementations must enforce:
 * - Per-step transaction commits (checkpoint pattern for SC-002)
 * - Immutability of executionId, revisionId, inputParameters, startedAt
 * - Atomic status updates with lastUpdatedAt timestamps
 * - Step results are append-only (never updated after insertion)
 */
interface IWorkflowExecutionRepository {
    
    /**
     * Create a new workflow execution record.
     * 
     * @param execution The execution to create
     * @return The created execution
     * @throws IllegalArgumentException if execution already exists
     */
    fun createExecution(execution: WorkflowExecution): WorkflowExecution
    
    /**
     * Save a step result (per-step commit for checkpoint pattern).
     * 
     * This method commits the step result immediately to ensure crash recovery.
     * Step results are append-only and never updated after insertion.
     * 
     * @param stepResult The step result to persist
     * @return The saved step result
     * @throws IllegalArgumentException if step result violates constraints (duplicate stepIndex, etc.)
     */
    fun saveStepResult(stepResult: ExecutionStepResult): ExecutionStepResult
    
    /**
     * Update execution status and related fields.
     * 
     * Updates status, errorMessage (if provided), completedAt (if terminal),
     * and lastUpdatedAt timestamp atomically.
     * 
     * @param executionId The execution identifier
     * @param status The new status
     * @param errorMessage Error message if status = FAILED, null otherwise
     * @throws io.maestro.core.errors.ExecutionNotFoundException if execution doesn't exist
     */
    fun updateExecutionStatus(
        executionId: WorkflowExecutionID,
        status: ExecutionStatus,
        errorMessage: String? = null
    )
    
    /**
     * Find execution by ID with all step results.
     * 
     * Returns execution with eagerly loaded step results ordered by stepIndex.
     * Returns null if execution doesn't exist.
     * 
     * @param executionId The execution identifier
     * @return The execution with step results, or null if not found
     */
    fun findById(executionId: WorkflowExecutionID): WorkflowExecution?
    
    /**
     * Find executions for a workflow revision with filtering and pagination.
     * 
     * Returns executions matching the revision, optionally filtered by status,
     * sorted by startedAt descending (most recent first), with pagination support.
     * 
     * @param revisionId The workflow revision identifier
     * @param status Optional status filter (null = all statuses)
     * @param limit Maximum number of results to return (default: 20, max: 100)
     * @param offset Number of results to skip for pagination (default: 0)
     * @return List of executions matching the criteria
     */
    fun findByWorkflowRevision(
        revisionId: WorkflowRevisionID,
        status: ExecutionStatus? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<WorkflowExecution>
    
    /**
     * Count executions for a workflow revision with optional status filter.
     * 
     * @param revisionId The workflow revision identifier
     * @param status Optional status filter (null = all statuses)
     * @return Count of executions matching the criteria
     */
    fun countByWorkflowRevision(
        revisionId: WorkflowRevisionID,
        status: ExecutionStatus? = null
    ): Long
}
