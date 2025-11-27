package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.ExecutionStepResult
import java.time.Instant

/**
 * Detailed response DTO for workflow execution.
 *
 * Extends ExecutionResponseDTO with completion details and step-by-step results.
 * Used when querying execution status for detailed monitoring.
 */
data class ExecutionDetailResponseDTO(
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

    @JsonProperty("completedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant?,

    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("steps")
    val steps: List<StepResultDTO>,

    @JsonProperty("_links")
    val links: Map<String, LinkDTO>
) {
    companion object {
        fun fromDomain(
            execution: WorkflowExecution,
            stepResults: List<ExecutionStepResult>
        ): ExecutionDetailResponseDTO {
            return ExecutionDetailResponseDTO(
                executionId = execution.executionId.toString(),
                status = execution.status.name,
                revisionId = RevisionIdDTO(
                    namespace = execution.revisionId.namespace,
                    id = execution.revisionId.id,
                    version = execution.revisionId.version
                ),
                inputParameters = execution.inputParameters,
                startedAt = execution.startedAt,
                completedAt = execution.completedAt,
                errorMessage = execution.errorMessage,
                steps = stepResults.map { StepResultDTO.fromDomain(it) },
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
