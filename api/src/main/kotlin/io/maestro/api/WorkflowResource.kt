package io.maestro.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.workflows.WorkflowYamlParser
import io.maestro.core.workflows.usecases.ActivateRevisionUseCase
import io.maestro.core.workflows.usecases.CreateRevisionUseCase
import io.maestro.core.workflows.usecases.CreateWorkflowUseCase
import io.maestro.core.workflows.usecases.DeactivateRevisionUseCase
import io.maestro.core.workflows.usecases.DeleteRevisionUseCase
import io.maestro.core.workflows.usecases.DeleteWorkflowUseCase
import io.maestro.core.workflows.usecases.UpdateRevisionUseCase
import io.maestro.api.errors.InvalidCurrentUpdatedAtHeaderException
import io.maestro.core.errors.WorkflowNotFoundException
import io.maestro.core.executions.usecases.GetExecutionHistoryUseCase
import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.Link
import io.maestro.model.Pagination
import io.maestro.model.WorkflowID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.maestro.model.execution.ExecutionHistoryResponse
import io.maestro.model.execution.ExecutionSummary
import io.maestro.model.execution.WorkflowExecution
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

/**
 * REST resource for workflow management operations.
 * Implements API contracts for workflow and revision management.
 *
 * This resource acts as the adapter layer, translating HTTP requests
 * to use case executions following Clean Architecture principles.
 */
@Path("/")
class WorkflowResource @Inject constructor(
    private val createWorkflowUseCase: CreateWorkflowUseCase,
    private val createRevisionUseCase: CreateRevisionUseCase,
    private val updateRevisionUseCase: UpdateRevisionUseCase,
    private val activateRevisionUseCase: ActivateRevisionUseCase,
    private val deactivateRevisionUseCase: DeactivateRevisionUseCase,
    private val deleteRevisionUseCase: DeleteRevisionUseCase,
    private val deleteWorkflowUseCase: DeleteWorkflowUseCase,
    private val repository: IWorkflowRevisionRepository,
    private val yamlParser: WorkflowYamlParser,  // For serializing responses only
    private val getExecutionHistoryUseCase: GetExecutionHistoryUseCase,
    private val executionRepository: IWorkflowExecutionRepository
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
    @Path(WORKFLOWS_PATH)
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun createWorkflow(yaml: String): Response {
        logger.info { "Received workflow creation request" }

        try {
            // Execute use case with raw YAML
            // The use case handles parsing, validation, and persistence
            val created: WorkflowRevisionWithSource = createWorkflowUseCase.execute(yaml)
            logger.info { "Successfully created workflow: ${created.toWorkflowRevisionID()}" }

            // Return the updated YAML source with metadata
            return Response.status(Response.Status.CREATED)
                .location(URI.create(getWorkflowRevisionIdPath(created)))
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
    @Path(WORKFLOW_ID_PATH)
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun createRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        yaml: String
    ): Response {
        logger.info { "Received revision creation request for $namespace/$id" }

        try {
            // Execute use case with namespace, id, and raw YAML
            val created = createRevisionUseCase.execute(namespace, id, yaml)
            logger.info { "Successfully created revision: ${created.toWorkflowRevisionID()}" }

            // Return the updated YAML source with metadata
            return Response.status(Response.Status.CREATED)
                .location(URI.create(getWorkflowRevisionIdPath(created)))
                .entity(created.yamlSource)
                .build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to create revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Lists all workflows in a namespace.
     *
     * Endpoint: GET /api/workflows/{namespace}
     *
     * Returns a list of all workflows (unique namespace+id combinations) in the specified namespace.
     *
     * @param namespace The workflow namespace
     * @return 200 OK with list of workflow IDs (as JSON), or empty list if no workflows found
     */
    @GET
    @Path(WORKFLOWS_NAMESPACE_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    fun listWorkflows(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String
    ): Response {
        logger.info { "Received list workflows request for namespace: $namespace" }

        try {
            val workflows = repository.listWorkflows(namespace)

            // Convert to simple DTO format
            val workflowList = workflows.map { workflowId ->
                mapOf(
                    "namespace" to workflowId.namespace,
                    "id" to workflowId.id
                )
            }

            logger.info { "Found ${workflows.size} workflows in namespace: $namespace" }
            return Response.ok(workflowList).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to list workflows in namespace $namespace: ${e.message}" }
            // Return empty list instead of error to handle gracefully in UI
            return Response.ok(emptyList<Map<String, String>>()).build()
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
    @Path(WORKFLOW_ID_PATH)
    @Produces("application/yaml", "application/x-yaml")
    fun listRevisions(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
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
    @Path(WORKFLOW_REVISION_ID_PATH)
    @Produces("application/yaml", "application/x-yaml")
    fun getRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @Suppress("UnresolvedRestParam") @PathParam("version") version: Int
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
     * Activates a workflow revision with optimistic locking.
     *
     * Endpoint: POST /api/workflows/{namespace}/{id}/{version}/activate
     * Header: X-Current-Updated-At (required) - The current updatedAt timestamp for optimistic locking
     *
     * Implements FR-009, FR-011 - Activate revisions with multi-active support.
     * Implements T099 - Optimistic locking using updatedAt field.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to activate
     * @param currentUpdatedAtHeader The current updatedAt timestamp from X-Current-Updated-At header
     * @return 200 OK with activated revision, 409 Conflict if optimistic lock fails, or error response
     */
    @POST
    @Path("$WORKFLOW_REVISION_ID_PATH/activate")
    @Produces("application/yaml", "application/x-yaml")
    fun activateRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @Suppress("UnresolvedRestParam") @PathParam("version") version: Int,
        @HeaderParam("X-Current-Updated-At") currentUpdatedAtHeader: String?
    ): Response {
        logger.info { "Received activation request for $namespace/$id/$version" }

        try {
            // Parse and validate the current updatedAt header
            val currentUpdatedAt = if (currentUpdatedAtHeader.isNullOrBlank()) {
                val ex = InvalidCurrentUpdatedAtHeaderException(null)
                logger.warn { ex.message }
                throw ex
            } else {
                try {
                    Instant.parse(currentUpdatedAtHeader)
                } catch (e: Exception) {
                    val ex = InvalidCurrentUpdatedAtHeaderException(currentUpdatedAtHeader)
                    logger.warn { ex.message }
                    throw ex
                }
            }

            // Execute activation use case with optimistic locking
            val activated = activateRevisionUseCase.execute(namespace, id, version, currentUpdatedAt)
            logger.info { "Successfully activated revision: $namespace/$id/$version" }

            // Return the updated YAML source with metadata
            return Response.ok(activated.yamlSource).build()

        } catch (e: Exception) {
            logger.error(e) { "Failed to activate revision: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

    /**
     * Deactivates a workflow revision with optimistic locking.
     *
     * Endpoint: POST /api/workflows/{namespace}/{id}/{version}/deactivate
     * Header: X-Current-Updated-At (required) - The current updatedAt timestamp for optimistic locking
     *
     * Implements FR-012 - Deactivate revisions.
     * Implements T099 - Optimistic locking using updatedAt field.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to deactivate
     * @param currentUpdatedAtHeader The current updatedAt timestamp from X-Current-Updated-At header
     * @return 200 OK with deactivated revision, 409 Conflict if optimistic lock fails, or error response
     */
    @POST
    @Path("$WORKFLOW_REVISION_ID_PATH/deactivate")
    @Produces("application/yaml", "application/x-yaml")
    fun deactivateRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @Suppress("UnresolvedRestParam") @PathParam("version") version: Int,
        @HeaderParam("X-Current-Updated-At") currentUpdatedAtHeader: String?
    ): Response {
        logger.info { "Received deactivation request for $namespace/$id/$version" }

        try {
            // Parse and validate the current updatedAt header
            val currentUpdatedAt = if (currentUpdatedAtHeader.isNullOrBlank()) {
                val ex = InvalidCurrentUpdatedAtHeaderException(null)
                logger.warn { ex.message }
                throw ex
            } else {
                try {
                    Instant.parse(currentUpdatedAtHeader)
                } catch (e: Exception) {
                    val ex = InvalidCurrentUpdatedAtHeaderException(currentUpdatedAtHeader)
                    logger.warn { ex.message }
                    throw ex
                }
            }

            // Execute deactivation use case with optimistic locking
            val deactivated = deactivateRevisionUseCase.execute(namespace, id, version, currentUpdatedAt)
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
     * Supports optimistic locking using the `updatedAt` field in the YAML body.
     * The update will only succeed if the updatedAt timestamp in the YAML matches
     * the current value in the database, preventing lost updates from concurrent
     * modifications.
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to update
     * @param yaml New YAML workflow definition (must include updatedAt field for optimistic locking)
     * @return 200 OK with updated revision, or 409 Conflict if optimistic lock fails
     */
    @PUT
    @Path(WORKFLOW_REVISION_ID_PATH)
    @Produces("application/yaml", "application/x-yaml")
    @Consumes("application/yaml", "application/x-yaml")
    fun updateRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @Suppress("UnresolvedRestParam") @PathParam("version") version: Int,
        yaml: String
    ): Response {
        logger.info { "Received update request for $namespace/$id/$version" }

        try {
            // Execute update use case with raw YAML (optimistic locking uses updatedAt from YAML)
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
    @Path(WORKFLOW_REVISION_ID_PATH)
    fun deleteRevision(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @Suppress("UnresolvedRestParam") @PathParam("version") version: Int
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
    @Path(WORKFLOW_ID_PATH)
    fun deleteWorkflow(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String
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

    /**
     * Get execution history for a workflow.
     *
     * Endpoint: GET /api/workflows/{namespace}/{id}/executions
     *
     * Implements User Story 4 (US4): View Execution History.
     * Returns paginated list of executions with optional filtering by version and status.
     *
     * @param namespace The workflow namespace
     * @param id The workflow identifier
     * @param version Optional version filter (null = all versions)
     * @param status Optional status filter (null = all statuses)
     * @param limit Maximum number of results to return (default: 20, max: 100)
     * @param offset Number of results to skip for pagination (default: 0)
     * @return 200 OK with execution history, or 404 if workflow not found
     */
    @GET
    @Path("$WORKFLOW_ID_PATH/executions")
    @Produces(MediaType.APPLICATION_JSON)
    fun getExecutionHistory(
        @Suppress("UnresolvedRestParam") @PathParam("namespace") namespace: String,
        @Suppress("UnresolvedRestParam") @PathParam("id") id: String,
        @QueryParam("version") version: Int?,
        @QueryParam("status") statusString: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?
    ): Response {
        logger.info { "Received history query for workflow: $namespace/$id, version=$version, status=$statusString" }

        try {
            // Verify workflow exists (check if any revision exists)
            val workflowIdObj = WorkflowID(namespace, id)
            val workflowExists = repository.exists(workflowIdObj)
            if (!workflowExists) {
                throw WorkflowNotFoundException(WorkflowRevisionID(namespace, id, 1))
            }

            // Parse status filter
            val status = statusString?.let {
                try {
                    ExecutionStatus.valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid status filter: $statusString, ignoring" }
                    null
                }
            }

            // Get history via use case
            val historyResult = getExecutionHistoryUseCase.getHistory(
                namespace = namespace,
                workflowId = id,
                version = version,
                status = status,
                limit = limit ?: 20,
                offset = offset ?: 0
            )

            // Convert executions to summary DTOs with step statistics
            val executionSummaries = historyResult.executions.map { execution: WorkflowExecution ->
                val stepResults = executionRepository.findStepResultsByExecutionId(execution.executionId)
                ExecutionSummary.fromDomain(execution, stepResults)
            }

            // Build pagination metadata
            val pagination = Pagination.create(
                total = historyResult.totalCount,
                limit = limit ?: 20,
                offset = offset ?: 0
            )

            // Build HATEOAS links
            val basePath = "/api/workflows/$namespace/$id/executions"
            val queryParams = mutableListOf<String>()
            version?.let { queryParams.add("version=$it") }
            statusString?.let { queryParams.add("status=$it") }
            queryParams.add("limit=${limit ?: 20}")
            val queryString = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
            
            val links = mutableMapOf<String, Link>()
            links["self"] = Link("$basePath$queryString")
            links["workflow"] = Link("/api/workflows/$namespace/$id")
            
            val nextOffset = (offset ?: 0) + (limit ?: 20)
            if (nextOffset < historyResult.totalCount) {
                val nextQueryParams = mutableListOf<String>()
                version?.let { nextQueryParams.add("version=$it") }
                statusString?.let { nextQueryParams.add("status=$it") }
                nextQueryParams.add("limit=${limit ?: 20}")
                nextQueryParams.add("offset=$nextOffset")
                links["next"] = Link("$basePath?${nextQueryParams.joinToString("&")}")
            }

            logger.info { "Successfully retrieved execution history: $namespace/$id (${executionSummaries.size} executions)" }

            // Return 200 OK with history
            return Response.ok()
                .entity(
                    ExecutionHistoryResponse(
                        executions = executionSummaries,
                        pagination = pagination,
                        links = links
                    )
                )
                .build()

        } catch (e: WorkflowNotFoundException) {
            logger.error(e) { "Workflow not found: $namespace/$id" }
            throw e
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Invalid request parameters: ${e.message}" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve execution history: ${e.message}" }
            throw e // Let exception mapper handle it
        }
    }

}