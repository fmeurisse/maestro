package io.maestro.model.tasks

import io.maestro.model.tasks.OrchestrationTask
import io.maestro.model.tasks.Step

data class If(
    val condition: String, // You might want to use a more structured expression class here
    val ifTrue: Step,      // This can be a TaskList or a single Action
    val ifFalse: Step? = null // This can also be a TaskList or a single Action
) : OrchestrationTask
