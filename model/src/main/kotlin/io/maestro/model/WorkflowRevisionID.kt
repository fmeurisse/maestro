package io.maestro.model

data class WorkflowRevisionID(
    override val namespace: String,
    override val id: String,
    override val version: Long
) : IWorkflowRevisionID