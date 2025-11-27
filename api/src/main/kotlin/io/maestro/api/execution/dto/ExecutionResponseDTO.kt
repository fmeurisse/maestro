package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.WorkflowExecution
import java.time.Instant

/**
 * Response DTO for workflow execution.
 *
 * Contains execution summary with HATEOAS links for navigation.
 * Used when creating a new execution or listing executions.
 */
data class ExecutionResponseDTO(
    @JsonProperty("executionId")
    val executionId: String,

    @JsonProperty("status")
    val status: String,

    @JsonProperty("revisionId")
    val revisionId: RevisionIdDTO,

    @JsonProperty("inputParameters")
    val inputParameters: Map<String, Any>,

    @JsonProperty("startedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,

    @JsonProperty("_links")
    val links: Map<String, LinkDTO>
) {
    companion object {
        fun fromDomain(execution: WorkflowExecution): ExecutionResponseDTO {
            return ExecutionResponseDTO(
                executionId = execution.executionId.toString(),
                status = execution.status.name,
                revisionId = RevisionIdDTO(
                    namespace = execution.revisionId.namespace,
                    id = execution.revisionId.id,
                    version = execution.revisionId.version
                ),
                inputParameters = execution.inputParameters,
                startedAt = execution.startedAt,
                links = mapOf(
                    "self" to LinkDTO("/api/executions/${execution.executionId}"),
                    "workflow" to LinkDTO(
                        "/api/workflows/${execution.revisionId.namespace}/${execution.revisionId.id}/${execution.revisionId.version}"
                    )
                )
            )
        }
    }
}

/**
 * Nested DTO for workflow revision reference.
 */
data class RevisionIdDTO(
    @JsonProperty("namespace")
    val namespace: String,

    @JsonProperty("id")
    val id: String,

    @JsonProperty("version")
    val version: Int
)

/**
 * HATEOAS link DTO.
 */
data class LinkDTO(
    @JsonProperty("href")
    val href: String
)
