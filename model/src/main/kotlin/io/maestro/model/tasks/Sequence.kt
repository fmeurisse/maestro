package io.maestro.model.tasks

import io.maestro.model.tasks.OrchestrationTask
import io.maestro.model.tasks.Step

data class Sequence(val tasks: List<Step>) : OrchestrationTask
