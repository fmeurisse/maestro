package io.maestro.model.execution

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.Link
import io.maestro.model.WorkflowRevisionID
import java.time.Instant

/**
 * Response DTO for workflow execution.
 *
 * Contains execution summary with HATEOAS links for navigation.
 * Used when creating a new execution or listing executions.
 */
data class ExecutionResponse(
    @param:JsonProperty("executionId")
    val executionId: String,

    @param:JsonProperty("status")
    val status: String,

    @param:JsonProperty("revisionId")
    val revisionId: WorkflowRevisionID,

    @param:JsonProperty("inputParameters")
    val inputParameters: Map<String, Any>,

    @param:JsonProperty("startedAt")
    @param:JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,

    @param:JsonProperty("_links")
    val links: Map<String, Link>
) {
    companion object {
        fun fromDomain(execution: WorkflowExecution): ExecutionResponse = ExecutionResponse(
            executionId = execution.executionId.toString(),
            status = execution.status.name,
            revisionId = execution.revisionId,
            inputParameters = execution.inputParameters,
            startedAt = execution.startedAt,
            links = mapOf(
                "self" to Link("/api/executions/${execution.executionId}"),
                "workflow" to Link(
                    "/api/workflows/${execution.revisionId.namespace}/${execution.revisionId.id}/${execution.revisionId.version}"
                )
            )
        )
    }
}