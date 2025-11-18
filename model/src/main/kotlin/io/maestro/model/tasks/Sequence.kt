package io.maestro.model.tasks

import io.maestro.model.tasks.OrchestrationTask
import io.maestro.model.tasks.Task

data class Sequence(val tasks: List<Task>) : OrchestrationTask
