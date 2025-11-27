package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.execution.ExecutionStepResult
import java.time.Instant

/**
 * DTO for individual step execution result.
 *
 * Contains detailed information about a single step's execution,
 * including inputs, outputs, errors, and timing information.
 */
data class StepResultDTO(
    @JsonProperty("stepIndex")
    val stepIndex: Int,

    @JsonProperty("stepId")
    val stepId: String,

    @JsonProperty("stepType")
    val stepType: String,

    @JsonProperty("status")
    val status: String,

    @JsonProperty("inputData")
    val inputData: Map<String, Any>?,

    @JsonProperty("outputData")
    val outputData: Map<String, Any>?,

    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("errorDetails")
    val errorDetails: Map<String, Any>?,

    @JsonProperty("startedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val startedAt: Instant,

    @JsonProperty("completedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant
) {
    companion object {
        fun fromDomain(stepResult: ExecutionStepResult): StepResultDTO {
            return StepResultDTO(
                stepIndex = stepResult.stepIndex,
                stepId = stepResult.stepId,
                stepType = stepResult.stepType,
                status = stepResult.status.name,
                inputData = stepResult.inputData,
                outputData = stepResult.outputData,
                errorMessage = stepResult.errorMessage,
                errorDetails = stepResult.errorDetails?.let { error ->
                    mapOf(
                        "errorType" to error.errorType,
                        "stackTrace" to error.stackTrace
                    )
                },
                startedAt = stepResult.startedAt,
                completedAt = stepResult.completedAt
            )
        }
    }
}
