package io.maestro.api.execution

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.api.execution.dto.ExecutionDetailResponseDTO
import io.maestro.api.execution.dto.ExecutionRequestDTO
import io.maestro.api.execution.dto.ExecutionResponseDTO
import io.maestro.api.execution.errors.ExecutionNotFoundException
import io.maestro.api.execution.errors.ParameterValidationException
import io.maestro.api.execution.errors.WorkflowNotFoundException
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.core.executions.usecases.ExecuteWorkflowUseCase
import io.maestro.core.executions.usecases.GetExecutionStatusUseCase
import io.maestro.core.parameters.ParameterValidator
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.WorkflowExecutionID
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.net.URI

/**
 * REST resource for workflow execution operations.
 *
 * Provides endpoints for:
 * - Triggering workflow execution with input parameters
 * - Querying execution status and results
 *
 * This resource follows Clean Architecture by delegating business logic
 * to use cases and acting as an adapter between HTTP and domain layer.
 *
 * Note: Full paths are specified on each method to avoid routing conflicts.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Workflow Execution", description = "Execute workflows and query execution status")
class ExecutionResource @Inject constructor(
    private val executeWorkflowUseCase: ExecuteWorkflowUseCase,
    private val getExecutionStatusUseCase: GetExecutionStatusUseCase,
    private val executionRepository: IWorkflowExecutionRepository,
    private val workflowRevisionRepository: IWorkflowRevisionRepository,
    private val parameterValidator: ParameterValidator
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Execute a workflow revision with input parameters.
     *
     * Endpoint: POST /api/executions
     *
     * Implements User Story 1 (US1): Execute workflow with valid parameters.
     * Per SC-001, execution initiation must complete in under 2 seconds.
     *
     * @param request The execution request containing workflow revision ID and input parameters
     * @return 200 OK with execution ID and status, or error response
     */
    @POST
    @Path("/api/executions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Execute a workflow",
        description = "Triggers execution of a workflow revision with input parameters. Returns execution ID for status tracking."
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200",
                description = "Execution started successfully",
                content = [Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ExecutionResponseDTO::class))]
            ),
            APIResponse(
                responseCode = "400",
                description = "Invalid parameters or validation failed",
                content = [Content(mediaType = "application/problem+json")]
            ),
            APIResponse(
                responseCode = "404",
                description = "Workflow revision not found",
                content = [Content(mediaType = "application/problem+json")]
            )
        ]
    )
    fun executeWorkflow(
        request: ExecutionRequestDTO
    ): Response {
        logger.info { "Received execution request for workflow: ${request.namespace}/${request.id}/v${request.version}" }

        try {
            // Build revision ID from request
            val revisionId = WorkflowRevisionID(request.namespace, request.id, request.version)

            // Verify workflow exists (for better error message)
            val workflow = workflowRevisionRepository.findById(revisionId)
                ?: throw WorkflowNotFoundException(revisionId)

            // Validate input parameters against workflow schema
            val validationResult = parameterValidator.validate(
                parameters = request.parameters,
                schema = workflow.parameters,
                revisionId = revisionId
            )

            // Throw exception if validation failed
            if (!validationResult.isValid) {
                throw ParameterValidationException(validationResult, revisionId)
            }

            // Execute workflow via use case with validated parameters
            val executionId = executeWorkflowUseCase.execute(
                revisionId = revisionId,
                inputParameters = validationResult.validatedParameters
            )

            // Fetch the execution to return status
            val execution = executionRepository.findById(executionId)
                ?: throw ExecutionNotFoundException(executionId)

            logger.info { "Successfully initiated execution: $executionId for workflow ${request.namespace}/${request.id}/v${request.version}" }

            // Return 200 OK with execution details
            return Response.ok()
                .location(URI.create("/api/executions/$executionId"))
                .entity(ExecutionResponseDTO.fromDomain(execution))
                .build()

        } catch (e: WorkflowNotFoundException) {
            logger.error(e) { "Workflow not found: ${request.namespace}/${request.id}/v${request.version}" }
            throw e
        } catch (e: ParameterValidationException) {
            logger.error(e) { "Parameter validation failed for workflow: ${request.namespace}/${request.id}/v${request.version}" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute workflow: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Get the status and details of a workflow execution.
     *
     * Endpoint: GET /api/executions/{executionId}
     *
     * Implements User Story 1 (US1): Query execution status.
     * Implements User Story 3 (US3): Track execution progress with step details.
     * Per SC-003, status queries must complete in under 1 second.
     *
     * @param executionId The execution ID (UUID v7 format)
     * @return 200 OK with execution details, or 404 if not found
     */
    @GET
    @Path("/api/executions/{executionId}")
    @Operation(
        summary = "Get execution status",
        description = "Retrieves detailed status and step-by-step results for a workflow execution."
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200",
                description = "Execution details retrieved successfully",
                content = [Content(mediaType = MediaType.APPLICATION_JSON, schema = Schema(implementation = ExecutionDetailResponseDTO::class))]
            ),
            APIResponse(
                responseCode = "404",
                description = "Execution not found",
                content = [Content(mediaType = "application/problem+json")]
            ),
            APIResponse(
                responseCode = "400",
                description = "Invalid execution ID format",
                content = [Content(mediaType = "application/problem+json")]
            )
        ]
    )
    fun getExecutionStatus(
        @PathParam("executionId")
        @Parameter(description = "Execution ID (NanoID format)", required = true)
        executionIdString: String
    ): Response {
        logger.info { "Received status query for execution: $executionIdString" }

        try {
            // Parse execution ID
            val executionId = WorkflowExecutionID.fromString(executionIdString)

            // Query execution status via use case
            val execution = getExecutionStatusUseCase.getStatus(executionId)
                ?: throw ExecutionNotFoundException(executionId)

            // Fetch step results for detailed response
            val stepResults = executionRepository.findStepResultsByExecutionId(executionId)

            logger.info { "Successfully retrieved execution status: $executionId (${execution.status})" }

            // Return 200 OK with detailed execution info
            return Response.ok()
                .entity(ExecutionDetailResponseDTO.fromDomain(execution, stepResults))
                .build()

        } catch (e: ExecutionNotFoundException) {
            logger.error(e) { "Execution not found: $executionIdString" }
            throw e
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Invalid execution ID format: $executionIdString" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve execution status: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

}
