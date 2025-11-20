package io.maestro.model.steps

data class Sequence(val tasks: List<Step>) : OrchestrationStep
