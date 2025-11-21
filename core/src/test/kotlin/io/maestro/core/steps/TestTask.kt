package io.maestro.core.steps

import io.maestro.model.steps.Task

/**
 * Test task step for unit testing StepTypeRegistry discovery.
 */
data class TestTask(val value: String) : Task {
    companion object {
        const val TYPE_NAME = "TestTask"
    }
}
