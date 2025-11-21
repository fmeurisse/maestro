package io.maestro.core.steps

import io.maestro.model.steps.OrchestrationStep
import io.maestro.model.steps.Step

data class Sequence(val tasks: List<Step>) : OrchestrationStep {
    companion object {
        const val TYPE_NAME = "Sequence"
    }
}
