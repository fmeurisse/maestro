package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.eclipse.microprofile.openapi.annotations.media.Schema

/**
 * Request DTO for triggering workflow execution.
 *
 * Contains the workflow revision ID and input parameters.
 * Parameters are validated against the workflow revision's parameter schema.
 */
@Schema(description = "Request to execute a workflow with input parameters")
data class ExecutionRequestDTO(
    @JsonProperty("namespace")
    @Schema(description = "Workflow namespace", example = "my-org", required = true)
    val namespace: String,

    @JsonProperty("id")
    @Schema(description = "Workflow identifier", example = "data-pipeline", required = true)
    val id: String,

    @JsonProperty("version")
    @Schema(description = "Workflow version number", example = "1", required = true)
    val version: Int,

    @JsonProperty("parameters")
    @Schema(description = "Input parameters for workflow execution", required = false)
    val parameters: Map<String, @JvmSuppressWildcards Any> = emptyMap()
)
