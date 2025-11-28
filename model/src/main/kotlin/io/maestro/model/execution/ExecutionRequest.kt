package io.maestro.model.execution

import org.eclipse.microprofile.openapi.annotations.media.Schema

/**
 * Request for triggering workflow execution.
 *
 * Contains the workflow revision ID and input parameters.
 * Parameters are validated against the workflow revision's parameter schema.
 */
@Schema(description = "Request to execute a workflow with input parameters")
data class ExecutionRequest(
    @param:Schema(description = "Workflow namespace", example = "my-org", required = true)
    val namespace: String,

    @param:Schema(description = "Workflow identifier", example = "data-pipeline", required = true)
    val id: String,

    @param:Schema(description = "Workflow version number", example = "1", required = true)
    val version: Int,

    @param:Schema(description = "Input parameters for workflow execution", required = false)
    val parameters: Map<String, @JvmSuppressWildcards Any> = emptyMap()
)