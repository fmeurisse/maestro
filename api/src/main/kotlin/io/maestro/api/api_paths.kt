package io.maestro.api

import io.maestro.model.IWorkflowID
import io.maestro.model.IWorkflowRevisionID

const val API_PREFIX = "/api"
const val EXECUTIONS_PATH = "$API_PREFIX/executions"
const val EXECUTION_ID_PATH = "$EXECUTIONS_PATH/{executionId}"
const val WORKFLOWS_PATH = "$API_PREFIX/workflows"
const val WORKFLOW_ID_PATH = "$WORKFLOWS_PATH/{namespace}/{id}"
const val WORKFLOW_REVISION_ID_PATH = "$WORKFLOW_ID_PATH/{version}"


fun getExecutionIdPath(executionId: String) = EXECUTION_ID_PATH.replace("{executionId}", executionId)

fun getWorkflowIdPath(id: IWorkflowID) = getWorkflowIdPath(id.namespace, id.id)
fun getWorkflowIdPath(namespace: String, id: String) =
    WORKFLOW_ID_PATH.replace("{namespace}", namespace).replace("{id}", id)

fun getWorkflowRevisionIdPath(id: IWorkflowRevisionID) = getWorkflowRevisionIdPath(id.namespace, id.id, id.version)
fun getWorkflowRevisionIdPath(id: IWorkflowID, version: Int) = getWorkflowRevisionIdPath(id.namespace, id.id, version)
fun getWorkflowRevisionIdPath(namespace: String, id: String, version: Int) =
    WORKFLOW_REVISION_ID_PATH.replace("{namespace}", namespace).replace("{id}", id)
        .replace("{version}", version.toString())