package io.maestro.model.execution

import io.maestro.model.util.NanoID
import java.time.Instant

/**
 * Represents the outcome of executing a single step within a workflow execution.
 * 
 * All fields are immutable (append-only pattern). Results never updated after insertion.
 * 
 * Business Rules:
 * - stepIndex must be unique within the parent executionId
 * - stepIndex values form contiguous sequence starting at 0
 * - errorMessage and errorDetails required when status = FAILED
 * - outputData should be null when status = FAILED (partial outputs discarded)
 * - completedAt - startedAt represents step execution duration
 */
data class ExecutionStepResult(
    /**
     * Unique identifier for this result
     * Primary key, NanoID format (URL-safe, 21 characters)
     */
    val resultId: String,
    
    /**
     * Parent execution reference
     * Immutable, foreign key to workflow_executions
     */
    val executionId: WorkflowExecutionID,
    
    /**
     * Ordinal position in execution sequence
     * Immutable, 0-based index
     */
    val stepIndex: Int,
    
    /**
     * Step identifier from workflow definition
     * Immutable, for correlation with workflow YAML
     */
    val stepId: String,
    
    /**
     * Step type name (e.g., "Sequence", "LogTask", "WorkTask")
     * Immutable, for display/filtering
     */
    val stepType: String,
    
    /**
     * Step execution outcome
     * Immutable once set (append-only)
     */
    val status: StepStatus,
    
    /**
     * Step input context at execution time
     * Immutable, null for steps without inputs
     */
    val inputData: Map<String, Any>? = null,
    
    /**
     * Step output/return value
     * Immutable, null for steps without outputs or failed steps
     */
    val outputData: Map<String, Any>? = null,
    
    /**
     * Human-readable error if failed
     * Null unless status = FAILED
     */
    val errorMessage: String? = null,
    
    /**
     * Detailed error information
     * Null unless status = FAILED
     */
    val errorDetails: ErrorInfo? = null,
    
    /**
     * Step execution start time
     * Immutable
     */
    val startedAt: Instant,
    
    /**
     * Step execution completion time
     * Immutable, must be >= startedAt
     */
    val completedAt: Instant
) {
    init {
        require(resultId.isNotBlank()) { "ExecutionStepResult.resultId must not be blank" }
        require(NanoID.isValid(resultId, minSize = 1, maxSize = 100)) {
            "ExecutionStepResult.resultId must be a valid NanoID format"
        }
        require(executionId != null) { "ExecutionStepResult.executionId must not be null" }
        require(stepIndex >= 0) { "ExecutionStepResult.stepIndex must be >= 0, got: $stepIndex" }
        require(stepId.isNotBlank()) { "ExecutionStepResult.stepId must not be blank" }
        require(stepType.isNotBlank()) { "ExecutionStepResult.stepType must not be blank" }
        require(status != null) { "ExecutionStepResult.status must not be null" }
        require(startedAt != null) { "ExecutionStepResult.startedAt must not be null" }
        require(completedAt != null) { "ExecutionStepResult.completedAt must not be null" }
        
        // Business rule: completedAt must be >= startedAt
        if (completedAt.isBefore(startedAt)) {
            throw IllegalArgumentException("ExecutionStepResult.completedAt must be >= startedAt")
        }
        
        // Business rule: errorMessage and errorDetails required when FAILED
        if (status == StepStatus.FAILED) {
            require(errorMessage != null) { 
                "ExecutionStepResult.errorMessage must be set when status = FAILED" 
            }
            require(errorDetails != null) { 
                "ExecutionStepResult.errorDetails must be set when status = FAILED" 
            }
        }
        
        // Business rule: errorMessage and errorDetails must be null when not FAILED
        if (status != StepStatus.FAILED) {
            if (errorMessage != null) {
                throw IllegalArgumentException(
                    "ExecutionStepResult.errorMessage must be null when status != FAILED"
                )
            }
            if (errorDetails != null) {
                throw IllegalArgumentException(
                    "ExecutionStepResult.errorDetails must be null when status != FAILED"
                )
            }
        }
        
        // Business rule: outputData should be null when FAILED (partial outputs discarded)
        if (status == StepStatus.FAILED && outputData != null) {
            // Warning: outputData present for failed step (partial output discarded)
            // This is allowed but unusual - consider logging
        }
    }
    
    companion object {
        /**
         * Create a completed step result.
         */
        fun createCompleted(
            resultId: String,
            executionId: WorkflowExecutionID,
            stepIndex: Int,
            stepId: String,
            stepType: String,
            inputData: Map<String, Any>? = null,
            outputData: Map<String, Any>? = null,
            startedAt: Instant,
            completedAt: Instant
        ): ExecutionStepResult {
            return ExecutionStepResult(
                resultId = resultId,
                executionId = executionId,
                stepIndex = stepIndex,
                stepId = stepId,
                stepType = stepType,
                status = StepStatus.COMPLETED,
                inputData = inputData,
                outputData = outputData,
                errorMessage = null,
                errorDetails = null,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }
        
        /**
         * Create a failed step result.
         */
        fun createFailed(
            resultId: String,
            executionId: WorkflowExecutionID,
            stepIndex: Int,
            stepId: String,
            stepType: String,
            inputData: Map<String, Any>? = null,
            errorMessage: String,
            errorDetails: ErrorInfo,
            startedAt: Instant,
            completedAt: Instant
        ): ExecutionStepResult {
            return ExecutionStepResult(
                resultId = resultId,
                executionId = executionId,
                stepIndex = stepIndex,
                stepId = stepId,
                stepType = stepType,
                status = StepStatus.FAILED,
                inputData = inputData,
                outputData = null, // Partial outputs discarded for failed steps
                errorMessage = errorMessage,
                errorDetails = errorDetails,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }
        
        /**
         * Create a skipped step result (fail-fast strategy).
         */
        fun createSkipped(
            resultId: String,
            executionId: WorkflowExecutionID,
            stepIndex: Int,
            stepId: String,
            stepType: String,
            startedAt: Instant
        ): ExecutionStepResult {
            return ExecutionStepResult(
                resultId = resultId,
                executionId = executionId,
                stepIndex = stepIndex,
                stepId = stepId,
                stepType = stepType,
                status = StepStatus.SKIPPED,
                inputData = null,
                outputData = null,
                errorMessage = null,
                errorDetails = null,
                startedAt = startedAt,
                completedAt = startedAt // Skipped steps complete immediately
            )
        }
    }
}
