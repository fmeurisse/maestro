package io.maestro.core.repository

import io.maestro.model.IWorkflowRevisionID
import io.maestro.model.WorkflowRevision

interface IWorkflowRevisionRepository {

    fun findById(id: IWorkflowRevisionID): WorkflowRevision?

    fun save(workflowRevision: WorkflowRevision): WorkflowRevision

    fun findAll(): List<WorkflowRevision>

    fun deleteById(id: IWorkflowRevisionID)

}