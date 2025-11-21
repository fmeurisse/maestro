package io.maestro.api.resource

import io.maestro.api.dto.WorkflowRevisionResponse
import io.maestro.core.parser.WorkflowYamlParser
import io.maestro.core.workflow.repository.IWorkflowRevisionRepository
import io.maestro.core.usecase.CreateWorkflowUseCase
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger

/**
 * REST resource for workflow management operations.
 * Implements API Contract 4.1.1 for workflow creation.
 *
 * This resource acts as the adapter layer, translating HTTP requests
 * to use case executions following Clean Architecture principles.
 */
@Path("/api/workflows")
@Produces("application/x-yaml")
@Consumes("application/x-yaml")
class WorkflowResource @Inject constructor(
    private val createWorkflowUseCase: CreateWorkflowUseCase,
    private val repository: IWorkflowRevisionRepository,
    private val yamlParser: WorkflowYamlParser  // For serializing responses only
) {

    private val log = Logger.getLogger(WorkflowResource::class.java)

    /**
     * Creates a new workflow with its first revision.
     *
     * Endpoint: POST /api/workflows
     * Content-Type: application/x-yaml
     *
     * Implements API Contract 4.1.1 and requirements REQ-WF-001 through REQ-WF-007.
     *
     * @param yaml YAML workflow definition
     * @return 201 Created with workflow revision, or error response
     */
    @POST
    fun createWorkflow(yaml: String): Response {
        log.info("Received workflow creation request")

        try {
            // Execute use case with raw YAML
            // The use case handles parsing, validation, and persistence
            val created = createWorkflowUseCase.execute(yaml)

            log.info("Successfully created workflow: ${created.namespace}/${created.id} v${created.version}")

            // Fetch the workflow WITH its YAML source for the response
            val revisionId = WorkflowRevisionID(created.namespace, created.id, created.version)
            val withSource = repository.findByIdWithSource(revisionId)
                ?: throw IllegalStateException("Failed to retrieve created workflow")

            // Convert to response DTO
            val response = withSource.toResponse()

            // Serialize to YAML and return 201 Created
            val responseYaml = yamlParser.toYaml(response)

            return Response.status(Response.Status.CREATED)
                .entity(responseYaml)
                .build()

        } catch (e: Exception) {
            log.error("Failed to create workflow", e)
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Extension function to convert WorkflowRevisionWithSource to response DTO
     */
    private fun WorkflowRevisionWithSource.toResponse() = WorkflowRevisionResponse(
        namespace = namespace,
        id = id,
        version = version,
        name = name,
        description = description,
        active = active,
        rootStep = steps, // Map steps to rootStep for response DTO
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
