package io.maestro.core.executions

import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.IStepExecutor
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.Step

/**
 * No-op implementation of IStepExecutor for unit tests.
 *
 * This executor simply executes steps without persisting results.
 * Use this in tests where you don't need actual persistence behavior
 * but need to provide a valid ExecutionContext.
 */
class NoOpStepExecutor : IStepExecutor {

    override fun executeAndPersist(
        step: Step,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext> {
        // Simply execute the step without persistence
        return step.execute(context)
    }

    override fun executeSequence(
        steps: List<Step>,
        context: ExecutionContext
    ): Pair<StepStatus, ExecutionContext> {
        var currentContext = context
        var finalStatus = StepStatus.COMPLETED

        for (step in steps) {
            val (status, updatedContext) = step.execute(currentContext)
            currentContext = updatedContext

            if (status == StepStatus.FAILED) {
                finalStatus = StepStatus.FAILED
                break
            }
        }

        return Pair(finalStatus, currentContext)
    }

    companion object {
        /**
         * Singleton instance for convenient test usage.
         */
        val INSTANCE = NoOpStepExecutor()
    }
}
