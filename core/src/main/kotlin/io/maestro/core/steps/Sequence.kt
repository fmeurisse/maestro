package io.maestro.core.steps

import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.OrchestrationStep
import io.maestro.model.steps.Step

data class Sequence(val steps: List<Step>) : OrchestrationStep {

    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        var currentContext = context

        for (step in steps) {
            // Execute with automatic persistence via step executor
            val (status, updatedContext) = context.stepExecutor.executeAndPersist(step, currentContext)

            // Fail-fast: if any step fails, stop execution and return failure
            if (status == StepStatus.FAILED) {
                return Pair(StepStatus.FAILED, updatedContext)
            }

            // Update context for next step
            currentContext = updatedContext
        }

        // All steps completed successfully
        return Pair(StepStatus.COMPLETED, currentContext)
    }
    
    companion object {
        const val TYPE_NAME = "Sequence"
    }
}
