package io.maestro.model.execution

/**
 * Represents the overall state of a workflow execution.
 * 
 * Status transitions:
 * - PENDING → RUNNING → COMPLETED
 * - PENDING → RUNNING → FAILED
 * - PENDING → RUNNING → CANCELLED (future enhancement)
 * 
 * Terminal states: COMPLETED, FAILED, CANCELLED (cannot transition back to RUNNING)
 */
enum class ExecutionStatus {
    /**
     * Execution created but not yet started
     */
    PENDING,
    
    /**
     * Execution in progress
     */
    RUNNING,
    
    /**
     * All steps completed successfully
     */
    COMPLETED,
    
    /**
     * Execution failed due to step error or timeout
     */
    FAILED,
    
    /**
     * Execution cancelled by user (future enhancement)
     */
    CANCELLED;
    
    /**
     * Returns true if this status is a terminal state (execution cannot continue).
     */
    fun isTerminal(): Boolean = this in listOf(COMPLETED, FAILED, CANCELLED)
}
