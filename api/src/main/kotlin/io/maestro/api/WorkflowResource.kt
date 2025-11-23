package io.maestro.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.usecase.CreateWorkflowUseCase
import io.maestro.model.WorkflowRevisionID
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Response
import java.net.URI

/**
 * REST resource for workflow management operations.
 * Implements API Contract 4.1.1 for workflow creation.
 *
 * This resource acts as the adapter layer, translating HTTP requests
 * to use case executions following Clean Architecture principles.
 */
@Path("/api/workflows")
class WorkflowResource @Inject constructor(
    private val createWorkflowUseCase: CreateWorkflowUseCase,
    private val yamlParser: WorkflowYamlParser  // For serializing responses only
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Creates a new workflow with its first revision.
     *
     * Endpoint: POST /api/workflows
     * Content-Type: application/yaml
     *
     * Implements API Contract 4.1.1 and requirements REQ-WF-001 through REQ-WF-007.
     *
     * @param yaml YAML workflow definition
     * @return 201 Created with workflow revision, or error response
     */
    @POST
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun createWorkflow(yaml: String): Response {
        logger.info { "Received workflow creation request" }

        try {
            // Execute use case with raw YAML
            // The use case handles parsing, validation, and persistence
            val createdId: WorkflowRevisionID = createWorkflowUseCase.execute(yaml)
            logger.info { "Successfully created workflow: $createdId" }

            // Serialize to YAML and return 201 Created
            val responseYaml = yamlParser.toYaml(createdId)

            return Response.status(Response.Status.CREATED)
                .location(URI.create("/api/workflows/${createdId.namespace}/${createdId.id}/${createdId.version}"))
                .entity(responseYaml)
                .build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to create workflow: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

}