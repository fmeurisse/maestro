package io.maestro.core.workflows.steps

import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.Task

data class LogTask(val message: String) : Task {

    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        // Perform logging
        println(message)
        
        // Logging always succeeds, return completed status with unchanged context
        return Pair(StepStatus.COMPLETED, context)
    }

    companion object {
        const val TYPE_NAME = "LogTask"
    }
}
