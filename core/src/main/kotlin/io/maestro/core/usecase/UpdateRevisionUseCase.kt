package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.maestro.model.errors.InvalidWorkflowRevisionException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.Instant

/**
 * Use case for updating an inactive workflow revision.
 * Implements FR-010 - Update inactive revisions in place.
 *
 * This use case allows modifications to inactive revisions while preventing
 * updates to active revisions to maintain execution consistency.
 *
 * Business Rules:
 * - Only inactive revisions can be updated (FR-010)
 * - Active revisions must be deactivated first before updating
 * - Version number and createdAt timestamp are immutable
 * - updatedAt timestamp is automatically updated
 *
 * Clean Architecture: Business logic isolated from infrastructure concerns.
 */
@ApplicationScoped
class UpdateRevisionUseCase constructor(
    private val repository: IWorkflowRevisionRepository,
    private val yamlParser: WorkflowYamlParser,
    private val clock: Clock
) {

    @Inject
    constructor(
        repository: IWorkflowRevisionRepository,
        yamlParser: WorkflowYamlParser
    ): this(repository, yamlParser, Clock.systemUTC())

    private val logger = KotlinLogging.logger {}

    /**
     * Updates an existing inactive workflow revision with new YAML definition.
     *
     * Implements:
     * - FR-010: Update inactive revisions
     * - REQ-WF-056: Parse and validate YAML
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param version The revision version to update
     * @param yaml New YAML definition
     * @return The updated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws ActiveRevisionConflictException if revision is active
     * @throws InvalidWorkflowRevisionException if namespace, id, or version don't match path parameters
     * @throws io.maestro.core.errors.WorkflowRevisionParsingException if YAML is invalid
     */
    fun execute(namespace: String, id: String, version: Int, yaml: String): WorkflowRevisionWithSource {
        logger.info { "Executing update revision use case for $namespace/$id/$version" }

        val revisionId = WorkflowRevisionID(namespace, id, version)

        // Check if the revision exists and get its current state
        val existing = repository.findById(revisionId)
            ?: throw WorkflowRevisionNotFoundException(revisionId)

        // Check if the revision is active (cannot update active revisions)
        if (existing.active) {
            logger.warn { "Attempt to update active revision: $revisionId" }
            throw ActiveRevisionConflictException(revisionId, "update")
        }

        logger.debug { "Parsing and validating new YAML for revision: $revisionId" }

        // Parse and validate the new YAML
        val parsedRevision = yamlParser.parseRevision(yaml, validate = true)

        // Ensure namespace, id, and version match the path parameters
        if (parsedRevision.namespace != namespace) {
            throw InvalidWorkflowRevisionException(
                "Namespace in YAML (${parsedRevision.namespace}) must match path parameter ($namespace)"
            )
        }
        if (parsedRevision.id != id) {
            throw InvalidWorkflowRevisionException(
                "Workflow ID in YAML (${parsedRevision.id}) must match path parameter ($id)"
            )
        }
        if (parsedRevision.version != version) {
            throw InvalidWorkflowRevisionException(
                "Version in YAML (${parsedRevision.version}) must match path parameter ($version)"
            )
        }

        logger.debug { "Creating updated revision with new data" }

        // Create the updated revision, preserving immutable fields
        val now = Instant.now(clock)
        val updatedRevisionData = existing.copy(
            name = parsedRevision.name,
            description = parsedRevision.description,
            steps = parsedRevision.steps,
            updatedAt = now  // Update timestamp
        )

        // Update YAML source with metadata (preserve version and createdAt, update updatedAt)
        logger.debug { "Updating YAML source with metadata" }
        val updatedYaml = WorkflowYamlMetadataUpdater.updateAllMetadata(
            yamlSource = yaml,
            version = version,
            createdAt = existing.createdAt,  // Preserve original creation time
            updatedAt = now
        )

        val updatedRevision = WorkflowRevisionWithSource(
            revision = updatedRevisionData,
            yamlSource = updatedYaml  // Store the updated YAML source
        )

        // Update in repository
        logger.debug { "Updating revision in repository: $revisionId" }
        val saved = repository.updateWithSource(updatedRevision)

        logger.info { "Successfully updated revision: $revisionId" }
        return saved
    }

    /**
     * Updates a workflow revision using WorkflowRevisionID.
     *
     * @param revisionId The complete revision identifier
     * @param yaml New YAML definition
     * @return The updated revision with updated YAML source
     * @throws WorkflowRevisionNotFoundException if revision doesn't exist
     * @throws ActiveRevisionConflictException if revision is active
     */
    fun execute(revisionId: WorkflowRevisionID, yaml: String): WorkflowRevisionWithSource {
        return execute(revisionId.namespace, revisionId.id, revisionId.version, yaml)
    }
}
