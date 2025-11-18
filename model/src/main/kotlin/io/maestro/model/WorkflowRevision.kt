package io.maestro.model

import io.maestro.model.tasks.Task

data class WorkflowRevision(
    override val namespace: String,
    override val id: String,
    override val version: Long,
    val task: Task
) : IWorkflowRevisionID