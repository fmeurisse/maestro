package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.errors.WorkflowNotFoundException
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.Instant

/**
 * Use case for creating a new revision of an existing workflow.
 * Implements REQ-WF-008: Sequential versioning for workflow revisions.
 *
 * This use case encapsulates the business logic for creating subsequent revisions
 * (version 2, 3, 4, ...) of an existing workflow following Clean Architecture principles.
 */
@ApplicationScoped
class CreateRevisionUseCase constructor(
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
     * Executes the revision creation use case.
     *
     * Implements:
     * - REQ-WF-008: Assign next sequential version number (max + 1)
     * - REQ-WF-003: Set active=false by default
     * - REQ-WF-005: Set createdAt and updatedAt timestamps
     * - REQ-WF-006: Validate step definition
     * - REQ-WF-007: Persist and return created entity
     *
     * @param namespace The workflow namespace
     * @param id The workflow ID
     * @param yaml Raw YAML string containing workflow definition
     * @return The created workflow revision ID
     * @throws WorkflowNotFoundException if workflow doesn't exist
     * @throws io.maestro.core.errors.WorkflowRevisionParsingException if validation or parsing fails
     */
    fun execute(namespace: String, id: String, yaml: String): WorkflowRevisionID {
        logger.info { "Executing revision creation use case for $namespace/$id" }

        val workflowId = WorkflowID(namespace, id)

        // Verify workflow exists
        if (!repository.exists(workflowId)) {
            logger.warn { "Workflow not found: $workflowId" }
            throw WorkflowNotFoundException(workflowId)
        }

        // Parse YAML to extract workflow data
        val parsedData = yamlParser.parseRevision(yaml, false)
        logger.debug { "Parsed workflow data for revision creation" }

        // REQ-WF-008: Get next version number (max + 1)
        val maxVersion = repository.findMaxVersion(workflowId) ?: 0
        val nextVersion = maxVersion + 1
        logger.debug { "Next version number: $nextVersion (max was $maxVersion)" }

        // Validate that parsed namespace/id matches the target workflow
        if (parsedData.namespace != namespace || parsedData.id != id) {
            logger.warn {
                "YAML namespace/id (${parsedData.namespace}/${parsedData.id}) " +
                "doesn't match target workflow ($namespace/$id)"
            }
            // Override with target namespace/id to ensure consistency
        }

        // REQ-WF-006: Validate workflow data
        logger.debug { "Validating workflow data" }
        val validatedData = parsedData.copy(namespace = namespace, id = id, version = nextVersion)
        validatedData.validate()

        // REQ-WF-003, REQ-WF-005: Create revision with defaults
        val now = Instant.now(clock)
        val revision = WorkflowRevision(
            namespace = namespace,
            id = id,
            version = nextVersion, // REQ-WF-008: Next sequential version
            name = validatedData.name,
            description = validatedData.description,
            active = false, // REQ-WF-003: Always start inactive
            steps = validatedData.steps,
            createdAt = now, // REQ-WF-005: Set creation timestamp
            updatedAt = now  // REQ-WF-005: Set update timestamp
        )

        // REQ-WF-007: Persist with YAML source and return
        logger.debug { "Persisting workflow revision: ${revision.namespace}/${revision.id}/${revision.version}" }
        val revisionWithSource = WorkflowRevisionWithSource.fromRevision(revision, yaml)
        val saved = repository.saveWithSource(revisionWithSource)
        logger.info { "Successfully created workflow revision: ${saved.toWorkflowRevisionID()}" }
        return saved.toWorkflowRevisionID()
    }
}
