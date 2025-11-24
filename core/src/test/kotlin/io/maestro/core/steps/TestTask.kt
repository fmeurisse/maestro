package io.maestro.core.steps

import io.maestro.model.steps.Task

/**
 * Test task step for unit testing StepTypeRegistry discovery.
 */
data class TestTask(val value: String) : Task {

    override fun execute() {
        println("Test task executed: $value")
    }

    companion object {
        const val TYPE_NAME = "TestTask"
    }

}
