package io.maestro.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.usecase.ActivateRevisionUseCase
import io.maestro.core.usecase.CreateRevisionUseCase
import io.maestro.core.usecase.CreateWorkflowUseCase
import io.maestro.core.usecase.DeactivateRevisionUseCase
import io.maestro.core.usecase.DeleteRevisionUseCase
import io.maestro.core.usecase.DeleteWorkflowUseCase
import io.maestro.core.usecase.UpdateRevisionUseCase
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevisionID
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import java.net.URI

/**
 * REST resource for workflow management operations.
 * Implements API contracts for workflow and revision management.
 *
 * This resource acts as the adapter layer, translating HTTP requests
 * to use case executions following Clean Architecture principles.
 */
@Path("/api/workflows")
class WorkflowResource @Inject constructor(
    private val createWorkflowUseCase: CreateWorkflowUseCase,
    private val createRevisionUseCase: CreateRevisionUseCase,
    private val updateRevisionUseCase: UpdateRevisionUseCase,
    private val activateRevisionUseCase: ActivateRevisionUseCase,
    private val deactivateRevisionUseCase: DeactivateRevisionUseCase,
    private val deleteRevisionUseCase: DeleteRevisionUseCase,
    private val deleteWorkflowUseCase: DeleteWorkflowUseCase,
    private val repository: IWorkflowRevisionRepository,
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
            val created = createWorkflowUseCase.execute(yaml)
            logger.info { "Successfully created workflow: ${created.toWorkflowRevisionID()}" }

            // Return the updated YAML source with metadata
            return Response.status(Response.Status.CREATED)
                .location(URI.create("/api/workflows/${created.namespace}/${created.id}/${created.version}"))
                .entity(created.yamlSource)
                .build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to create workflow: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Creates a new revision of an existing workflow.
     *
     * Endpoint: POST /api/workflows/{namespace}/{id}
     * Content-Type: application/yaml
     *
     * Implements API Contract for revision creation (REQ-WF-008).
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param yaml YAML workflow definition
     * @return 201 Created with workflow revision ID, or error response
     */
    @POST
    @Path("/{namespace}/{id}")
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun createRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        yaml: String
    ): Response {
        logger.info { "Received revision creation request for $namespace/$id" }

        try {
            // Execute use case with namespace, id, and raw YAML
            val created = createRevisionUseCase.execute(namespace, id, yaml)
            logger.info { "Successfully created revision: ${created.toWorkflowRevisionID()}" }

            // Return the updated YAML source with metadata
            return Response.status(Response.Status.CREATED)
                .location(URI.create("/api/workflows/${created.namespace}/${created.id}/${created.version}"))
                .entity(created.yamlSource)
                .build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to create revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Lists all revisions of a workflow.
     *
     * Endpoint: GET /api/workflows/{namespace}/{id}
     *
     * Implements API Contract for listing workflow revisions.
     * Supports ?active=true query parameter to filter active revisions only.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param active Optional filter for active revisions only
     * @return 200 OK with list of revision IDs, or 404 if workflow not found
     */
    @GET
    @Path("/{namespace}/{id}")
    @Produces("application/yaml", "application/x-yaml")
    fun listRevisions(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @QueryParam("active") active: Boolean?
    ): Response {
        logger.info { "Received list revisions request for $namespace/$id (active filter: $active)" }

        try {
            val workflowId = WorkflowID(namespace, id)

            // Get revisions based on active filter
            val revisions = if (active == true) {
                repository.findActiveRevisions(workflowId)
            } else {
                repository.findByWorkflowId(workflowId)
            }

            if (revisions.isEmpty()) {
                logger.warn { "No revisions found for: $workflowId (active filter: $active)" }
                return Response.status(Response.Status.NOT_FOUND).build()
            }

            // Convert to revision IDs and return as YAML list
            val revisionIds = revisions.map {
                mapOf(
                    "namespace" to it.namespace,
                    "id" to it.id,
                    "version" to it.version,
                    "active" to it.active
                )
            }

            logger.info { "Found ${revisions.size} revisions for $workflowId" }

            // Serialize to YAML using the yamlParser
            val responseYaml = yamlParser.toYaml(revisionIds)
            return Response.ok(responseYaml).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to list revisions: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Gets a specific workflow revision.
     *
     * Endpoint: GET /api/workflows/{namespace}/{id}/{version}
     *
     * Implements API Contract for retrieving a specific revision.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version number
     * @return 200 OK with revision details, or 404 if not found
     */
    @GET
    @Path("/{namespace}/{id}/{version}")
    @Produces("application/yaml", "application/x-yaml")
    fun getRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @PathParam("version") version: Int
    ): Response {
        logger.info { "Received get revision request for $namespace/$id/$version" }

        try {
            val revisionId = WorkflowRevisionID(namespace, id, version)
            val revision = repository.findByIdWithSource(revisionId)

            if (revision == null) {
                logger.warn { "Revision not found: $revisionId" }
                return Response.status(Response.Status.NOT_FOUND).build()
            }

            // Return the original YAML source
            logger.info { "Found revision: $revisionId" }

            return Response.ok(revision.yamlSource).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to get revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Activates a workflow revision.
     *
     * Endpoint: POST /api/workflows/{namespace}/{id}/{version}/activate
     *
     * Implements FR-009, FR-011 - Activate revisions with multi-active support.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to activate
     * @return 200 OK with activated revision, or error response
     */
    @POST
    @Path("/{namespace}/{id}/{version}/activate")
    @Produces("application/yaml", "application/x-yaml")
    fun activateRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @PathParam("version") version: Int
    ): Response {
        logger.info { "Received activation request for $namespace/$id/$version" }

        try {
            // Execute activation use case
            val activated = activateRevisionUseCase.execute(namespace, id, version)
            logger.info { "Successfully activated revision: $namespace/$id/$version" }

            // Return the updated YAML source with metadata
            return Response.ok(activated.yamlSource).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to activate revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Deactivates a workflow revision.
     *
     * Endpoint: POST /api/workflows/{namespace}/{id}/{version}/deactivate
     *
     * Implements FR-012 - Deactivate revisions.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to deactivate
     * @return 200 OK with deactivated revision, or error response
     */
    @POST
    @Path("/{namespace}/{id}/{version}/deactivate")
    @Produces("application/yaml", "application/x-yaml")
    fun deactivateRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @PathParam("version") version: Int
    ): Response {
        logger.info { "Received deactivation request for $namespace/$id/$version" }

        try {
            // Execute deactivation use case
            val deactivated = deactivateRevisionUseCase.execute(namespace, id, version)
            logger.info { "Successfully deactivated revision: $namespace/$id/$version" }

            // Return the updated YAML source with metadata
            return Response.ok(deactivated.yamlSource).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to deactivate revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Updates an inactive workflow revision.
     *
     * Endpoint: PUT /api/workflows/{namespace}/{id}/{version}
     * Content-Type: application/yaml
     *
     * Implements FR-010 - Update inactive revisions in place.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to update
     * @param yaml New YAML workflow definition
     * @return 200 OK with updated revision, or error response
     */
    @PUT
    @Path("/{namespace}/{id}/{version}")
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun updateRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @PathParam("version") version: Int,
        yaml: String
    ): Response {
        logger.info { "Received update request for $namespace/$id/$version" }

        try {
            // Execute update use case with raw YAML
            val updated = updateRevisionUseCase.execute(namespace, id, version, yaml)
            logger.info { "Successfully updated revision: ${updated.toWorkflowRevisionID()}" }

            // Return the updated YAML source with metadata
            return Response.ok(updated.yamlSource).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to update revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Deletes a specific inactive workflow revision.
     *
     * Endpoint: DELETE /api/workflows/{namespace}/{id}/{version}
     *
     * Implements FR-014 - Delete individual revisions.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to delete
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{namespace}/{id}/{version}")
    fun deleteRevision(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String,
        @PathParam("version") version: Int
    ): Response {
        logger.info { "Received delete revision request for $namespace/$id/$version" }

        try {
            // Execute delete use case
            deleteRevisionUseCase.execute(namespace, id, version)
            logger.info { "Successfully deleted revision: $namespace/$id/$version" }

            // Return 204 No Content
            return Response.noContent().build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to delete revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Deletes all revisions of a workflow.
     *
     * Endpoint: DELETE /api/workflows/{namespace}/{id}
     *
     * Implements FR-015 - Delete entire workflows.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @return 204 No Content on success (even if no revisions were deleted)
     */
    @DELETE
    @Path("/{namespace}/{id}")
    fun deleteWorkflow(
        @PathParam("namespace") namespace: String,
        @PathParam("id") id: String
    ): Response {
        logger.info { "Received delete workflow request for $namespace/$id" }

        try {
            // Execute delete use case
            val deletedCount = deleteWorkflowUseCase.execute(namespace, id)
            logger.info { "Successfully deleted $deletedCount revisions for workflow: $namespace/$id" }

            // Return 204 No Content regardless of count
            return Response.noContent().build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to delete workflow: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

}