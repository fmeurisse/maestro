package io.maestro.model.execution

import io.maestro.model.WorkflowRevisionID
import java.time.Instant

/**
 * Represents a single run of a workflow revision with its overall execution state.
 * 
 * Immutable fields: executionId, revisionId, inputParameters, startedAt
 * Mutable fields: status, errorMessage, completedAt, lastUpdatedAt
 * 
 * Business Rules:
 * - executionId generated using NanoID (URL-safe, 21 characters) for global uniqueness
 * - inputParameters must match the parameter schema defined in revisionId (validated before execution starts)
 * - status transitions are unidirectional: cannot go from terminal state (COMPLETED/FAILED/CANCELLED) back to RUNNING
 * - completedAt must be >= startedAt when set
 * - lastUpdatedAt updated atomically with any state change
 */
data class WorkflowExecution(
    /**
     * Unique identifier (NanoID format, URL-safe)
     * Primary key, immutable
     */
    val executionId: WorkflowExecutionID,
    
    /**
     * Reference to executed workflow revision
     * Immutable, foreign key to workflow_revisions
     */
    val revisionId: WorkflowRevisionID,
    
    /**
     * Input parameters provided for execution
     * Immutable, validated against revision schema
     */
    val inputParameters: Map<String, Any>,
    
    /**
     * Current execution state
     * Mutable: PENDING → RUNNING → COMPLETED|FAILED|CANCELLED
     */
    val status: ExecutionStatus,
    
    /**
     * Human-readable error message if failed
     * Null unless status = FAILED
     */
    val errorMessage: String? = null,
    
    /**
     * Execution start timestamp
     * Immutable, set on creation
     */
    val startedAt: Instant,
    
    /**
     * Execution completion timestamp
     * Null while RUNNING/PENDING, set when terminal state reached
     */
    val completedAt: Instant? = null,
    
    /**
     * Last state change timestamp
     * Updated on every status change
     */
    val lastUpdatedAt: Instant
) {
    init {
        require(executionId != null) { "WorkflowExecution.executionId must not be null" }
        require(revisionId != null) { "WorkflowExecution.revisionId must not be null" }
        require(inputParameters != null) { "WorkflowExecution.inputParameters must not be null" }
        require(status != null) { "WorkflowExecution.status must not be null" }
        require(startedAt != null) { "WorkflowExecution.startedAt must not be null" }
        require(lastUpdatedAt != null) { "WorkflowExecution.lastUpdatedAt must not be null" }
        
        // Business rule: errorMessage required when FAILED
        if (status == ExecutionStatus.FAILED && errorMessage == null) {
            throw IllegalArgumentException("WorkflowExecution.errorMessage must be set when status = FAILED")
        }
        
        // Business rule: errorMessage must be null when not FAILED
        if (status != ExecutionStatus.FAILED && errorMessage != null) {
            throw IllegalArgumentException("WorkflowExecution.errorMessage must be null when status != FAILED")
        }
        
        // Business rule: completedAt must be >= startedAt when set
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw IllegalArgumentException("WorkflowExecution.completedAt must be >= startedAt")
        }
        
        // Business rule: completedAt must be set for terminal states
        if (status.isTerminal() && completedAt == null) {
            throw IllegalArgumentException("WorkflowExecution.completedAt must be set when status is terminal")
        }
        
        // Business rule: completedAt must be null for non-terminal states
        if (!status.isTerminal() && completedAt != null) {
            throw IllegalArgumentException("WorkflowExecution.completedAt must be null when status is not terminal")
        }
    }
    
    /**
     * Create a copy with updated status and lastUpdatedAt timestamp.
     */
    fun withStatus(newStatus: ExecutionStatus, now: Instant = Instant.now()): WorkflowExecution {
        return copy(status = newStatus, lastUpdatedAt = now)
    }
    
    /**
     * Create a copy with updated status, error message, and timestamps.
     */
    fun withFailure(errorMsg: String, now: Instant = Instant.now()): WorkflowExecution {
        return copy(
            status = ExecutionStatus.FAILED,
            errorMessage = errorMsg,
            completedAt = now,
            lastUpdatedAt = now
        )
    }
    
    /**
     * Create a copy with completion status and timestamp.
     */
    fun withCompletion(now: Instant = Instant.now()): WorkflowExecution {
        return copy(
            status = ExecutionStatus.COMPLETED,
            completedAt = now,
            lastUpdatedAt = now
        )
    }
    
    companion object {
        /**
         * Create a new WorkflowExecution in PENDING status.
         */
        fun create(
            executionId: WorkflowExecutionID,
            revisionId: WorkflowRevisionID,
            inputParameters: Map<String, Any>,
            now: Instant = Instant.now()
        ): WorkflowExecution {
            return WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = inputParameters,
                status = ExecutionStatus.PENDING,
                errorMessage = null,
                startedAt = now,
                completedAt = null,
                lastUpdatedAt = now
            )
        }
        
        /**
         * Create a new WorkflowExecution in RUNNING status (after execution starts).
         */
        fun start(
            executionId: WorkflowExecutionID,
            revisionId: WorkflowRevisionID,
            inputParameters: Map<String, Any>,
            now: Instant = Instant.now()
        ): WorkflowExecution {
            return WorkflowExecution(
                executionId = executionId,
                revisionId = revisionId,
                inputParameters = inputParameters,
                status = ExecutionStatus.RUNNING,
                errorMessage = null,
                startedAt = now,
                completedAt = null,
                lastUpdatedAt = now
            )
        }
    }
}
