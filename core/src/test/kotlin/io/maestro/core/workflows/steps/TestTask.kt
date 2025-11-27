package io.maestro.core.workflows.steps

import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.Task

/**
 * Test task step for unit testing StepTypeRegistry discovery.
 */
data class TestTask(val value: String) : Task {

    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        println("Test task executed: $value")
        return Pair(StepStatus.COMPLETED, context)
    }

    companion object {
        const val TYPE_NAME = "TestTask"
    }

}
