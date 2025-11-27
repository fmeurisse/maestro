package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.execution.WorkflowExecution
import java.time.Instant

/**
 * Summary DTO for execution history list items.
 *
 * Contains essential execution information for display in history lists,
 * including execution ID, status, version, timing, and step statistics.
 */
data class ExecutionSummaryDTO(
    @JsonProperty("executionId")
    val executionId: String,

    @JsonProperty("status")
    val status: String,

    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("revisionVersion")
    val revisionVersion: Int,

    @JsonProperty("startedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,

    @JsonProperty("completedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant?,

    @JsonProperty("stepCount")
    val stepCount: Int,

    @JsonProperty("completedSteps")
    val completedSteps: Int,

    @JsonProperty("failedSteps")
    val failedSteps: Int
) {
    companion object {
        fun fromDomain(
            execution: WorkflowExecution,
            stepResults: List<io.maestro.model.execution.ExecutionStepResult>
        ): ExecutionSummaryDTO {
            val stepCount = stepResults.size
            val completedSteps = stepResults.count { it.status == io.maestro.model.execution.StepStatus.COMPLETED }
            val failedSteps = stepResults.count { it.status == io.maestro.model.execution.StepStatus.FAILED }

            return ExecutionSummaryDTO(
                executionId = execution.executionId.toString(),
                status = execution.status.name,
                errorMessage = execution.errorMessage,
                revisionVersion = execution.revisionId.version,
                startedAt = execution.startedAt,
                completedAt = execution.completedAt,
                stepCount = stepCount,
                completedSteps = completedSteps,
                failedSteps = failedSteps
            )
        }
    }
}
