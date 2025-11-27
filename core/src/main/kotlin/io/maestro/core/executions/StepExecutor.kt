package io.maestro.core.executions

import io.maestro.model.execution.*
import io.maestro.model.steps.Step
import io.maestro.model.util.NanoID
import java.time.Instant

/**
 * Executor that wraps step execution with automatic result persistence.
 *
 * This class implements IStepExecutor interface (defined in model module)
 * to allow ExecutionContext to reference it without circular dependencies.
 *
 * Encapsulates the logic for:
 * - Executing a step with context
 * - Persisting the step result (per-step commit for crash recovery)
 * - Handling errors and failures
 * - Maintaining step index sequence
 *
 * Used by both:
 * - ExecuteWorkflowUseCase for top-level workflow execution
 * - OrchestrationSteps (Sequence, If) for executing child steps
 */
class StepExecutor(
    private val executionId: WorkflowExecutionID,
    private val executionRepository: IWorkflowExecutionRepository
) : IStepExecutor {
    private var currentStepIndex = 0

    /**
     * Execute a step and persist its result.
     *
     * @param step The step to execute
     * @param context The execution context
     * @return Pair of (StepStatus, updated ExecutionContext)
     */
    override fun executeAndPersist(
        step: Step,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext> {
        val stepIndex = currentStepIndex++
        val startTime = Instant.now()

        // Execute the step
        var errorInfo: ErrorInfo? = null
        val (status, updatedContext) = try {
            step.execute(context)
        } catch (e: Exception) {
            // Step execution failed with exception
            errorInfo = ErrorInfo(
                errorType = e::class.simpleName ?: "Unknown",
                stackTrace = e.stackTraceToString(),
                stepInputs = null // TODO: Extract from context if needed
            )

            val failedContext = context
            Pair(StepStatus.FAILED, failedContext)
        }

        val endTime = Instant.now()

        // Persist step result (per-step commit for crash recovery)
        val stepResult = ExecutionStepResult(
            resultId = NanoID.generate(),
            executionId = executionId,
            stepIndex = stepIndex,
            stepId = step.toString(), // Simple representation for now
            stepType = step::class.simpleName ?: "Unknown",
            status = status,
            inputData = null, // TODO: Extract input data from context
            outputData = null, // TODO: Extract output data from updatedContext
            errorMessage = if (status == StepStatus.FAILED) "Step execution failed" else null,
            errorDetails = errorInfo,
            startedAt = startTime,
            completedAt = endTime
        )

        executionRepository.saveStepResult(stepResult)

        return Pair(status, updatedContext)
    }

    /**
     * Execute multiple steps sequentially with automatic persistence.
     *
     * Stops on first failure (fail-fast strategy).
     *
     * @param steps The steps to execute
     * @param context The initial execution context
     * @return Pair of (final StepStatus, final ExecutionContext)
     */
    override fun executeSequence(
        steps: List<Step>,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext> {
        var currentContext = context
        var finalStatus = StepStatus.COMPLETED

        for (step in steps) {
            val (status, updatedContext) = executeAndPersist(step, currentContext)
            currentContext = updatedContext

            if (status == StepStatus.FAILED) {
                finalStatus = StepStatus.FAILED
                break // Fail-fast
            }
        }

        return Pair(finalStatus, currentContext)
    }

    /**
     * Get the current step index (for external tracking).
     */
    fun getCurrentStepIndex(): Int = currentStepIndex
}
