package io.maestro.model.execution

import io.maestro.model.steps.Step

/**
 * Interface for step execution with automatic result persistence.
 *
 * This interface is defined in the model module to allow ExecutionContext
 * to reference it without circular dependencies. The implementation lives
 * in the core module.
 *
 * Implementations handle:
 * - Executing steps with execution context
 * - Persisting step results after each execution (checkpoint pattern)
 * - Maintaining step index sequence
 * - Error handling and status tracking
 */
interface IStepExecutor {

    /**
     * Execute a step and persist its result.
     *
     * @param step The step to execute
     * @param context The execution context
     * @return Pair of (StepStatus, updated ExecutionContext)
     */
    fun executeAndPersist(
        step: Step,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext>

    /**
     * Execute multiple steps sequentially with automatic persistence.
     *
     * Stops on first failure (fail-fast strategy).
     *
     * @param steps The steps to execute
     * @param context The initial execution context
     * @return Pair of (final StepStatus, final ExecutionContext)
     */
    fun executeSequence(
        steps: List<Step>,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext>
}
