package io.maestro.model.execution

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.Link
import io.maestro.model.WorkflowRevisionID
import java.time.Instant

/**
 * Detailed response for workflow execution.
 */
data class ExecutionDetailResponse(
    val executionId: String,
    val status: String,
    val revisionId: WorkflowRevisionID,
    val inputParameters: Map<String, Any>,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant?,
    val errorMessage: String?,
    val steps: List<ExecutionStepResult>,
    @param:JsonProperty("_links")
    val links: Map<String, Link>
) {
    companion object {
        fun fromDomain(
            execution: WorkflowExecution,
            stepResults: List<ExecutionStepResult>
        ): ExecutionDetailResponse {
            return ExecutionDetailResponse(
                executionId = execution.executionId.toString(),
                status = execution.status.name,
                revisionId = execution.revisionId,
                inputParameters = execution.inputParameters,
                startedAt = execution.startedAt,
                completedAt = execution.completedAt,
                errorMessage = execution.errorMessage,
                steps = stepResults,
                links = mapOf(
                    "self" to Link("/api/executions/${execution.executionId}"),
                    "workflow" to Link(
                        "/api/workflows/${execution.revisionId.namespace}/${execution.revisionId.id}/${execution.revisionId.version}"
                    )
                )
            )
        }
    }
}