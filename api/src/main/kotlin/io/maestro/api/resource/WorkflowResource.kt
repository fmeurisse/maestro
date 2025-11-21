package io.maestro.api.resource

import io.maestro.api.dto.WorkflowRevisionResponse
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.usecase.CreateWorkflowUseCase
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI

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

    private val logger = KotlinLogging.logger {}

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
