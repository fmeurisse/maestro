package io.maestro.core.steps

import io.maestro.model.steps.Task

data class LogTask(val message: String) : Task {
    companion object {
        const val TYPE_NAME = "LogTask"
    }
}
