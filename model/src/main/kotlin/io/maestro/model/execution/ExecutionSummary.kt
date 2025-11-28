package io.maestro.model.execution

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Summary for execution history list items.
 *
 * Contains essential execution information for display in history lists,
 * including execution ID, status, version, timing, and step statistics.
 */
data class ExecutionSummary(
    @param:JsonProperty("executionId")
    val executionId: String,

    @param:JsonProperty("status")
    val status: String,

    @param:JsonProperty("errorMessage")
    val errorMessage: String?,

    @param:JsonProperty("revisionVersion")
    val revisionVersion: Int,

    @param:JsonProperty("startedAt")
    @param:JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,

    @param:JsonProperty("completedAt")
    @param:JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant?,

    @param:JsonProperty("stepCount")
    val stepCount: Int,

    @param:JsonProperty("completedSteps")
    val completedSteps: Int,

    @param:JsonProperty("failedSteps")
    val failedSteps: Int
) {
    companion object {
        fun fromDomain(
            execution: WorkflowExecution,
            stepResults: List<ExecutionStepResult>
        ): ExecutionSummary {
            val stepCount = stepResults.size
            val completedSteps = stepResults.count { it.status == StepStatus.COMPLETED }
            val failedSteps = stepResults.count { it.status == StepStatus.FAILED }

            return ExecutionSummary(
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