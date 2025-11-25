package io.maestro.model.execution

/**
 * Represents the outcome of a single step execution.
 * 
 * Status values:
 * - PENDING: Step not yet executed (initial state)
 * - RUNNING: Step currently executing (transient, not persisted in v1)
 * - COMPLETED: Step completed successfully
 * - FAILED: Step failed with error
 * - SKIPPED: Step not executed due to earlier failure in workflow (fail-fast strategy)
 */
enum class StepStatus {
    /**
     * Step not yet executed (initial state)
     */
    PENDING,
    
    /**
     * Step currently executing (transient state, exists only during active execution)
     */
    RUNNING,
    
    /**
     * Step completed successfully
     */
    COMPLETED,
    
    /**
     * Step failed with error
     */
    FAILED,
    
    /**
     * Step not executed due to earlier failure in workflow (fail-fast strategy)
     */
    SKIPPED;
    
    /**
     * Returns true if this status indicates the step completed (successfully or with failure).
     */
    fun isTerminal(): Boolean = this in listOf(COMPLETED, FAILED, SKIPPED)
}
