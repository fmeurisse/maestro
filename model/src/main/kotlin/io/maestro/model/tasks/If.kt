package io.maestro.model.tasks

import io.maestro.model.tasks.OrchestrationTask
import io.maestro.model.tasks.Task

data class If(
    val condition: String, // You might want to use a more structured expression class here
    val ifTrue: Task,      // This can be a TaskList or a single Action
    val ifFalse: Task? = null // This can also be a TaskList or a single Action
) : OrchestrationTask
