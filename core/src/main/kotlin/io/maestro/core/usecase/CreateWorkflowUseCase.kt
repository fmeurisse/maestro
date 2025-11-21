package io.maestro.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.exception.WorkflowAlreadyExistsException
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.Instant

/**
 * Use case for creating a new workflow with its first revision.
 * Implements REQ-WF-001 through REQ-WF-007.
 *
 * This use case encapsulates the business logic for workflow creation following
 * Clean Architecture principles - single responsibility, framework-agnostic.
 */
@ApplicationScoped
class CreateWorkflowUseCase @Inject constructor(
    private val repository: IWorkflowRevisionRepository,
    private val yamlParser: WorkflowYamlParser,
    private val clock: Clock = Clock.systemUTC()
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Executes the workflow creation use case.
     *
     * Implements:
     * - REQ-WF-001: Create workflow with namespace, id, description, rootStep
     * - REQ-WF-002: Assign version 1 to first revision
     * - REQ-WF-003: Set active=false by default
     * - REQ-WF-004: Validate uniqueness of namespace+id
     * - REQ-WF-005: Set createdAt and updatedAt timestamps
     * - REQ-WF-006: Validate step definition
     * - REQ-WF-007: Persist and return created entity
     *
     * @param yaml Raw YAML string containing workflow definition
     * @return The created workflow revision (without YAML source)
     * @throws WorkflowAlreadyExistsException if workflow already exists (REQ-WF-004)
     * @throws io.maestro.core.exception.WorkflowRevisionParsingException if validation or parsing fails (REQ-WF-006)
     */
    fun execute(yaml: String): WorkflowRevisionID {
        logger.info { "Executing workflow creation use case" }

        // Parse YAML to extract workflow data // REQ-WF-002: First revision is version 1
        val parsedData = yamlParser.parseRevision(yaml, false).copy(version = 1)
        logger.debug { "Parsed workflow data: ${parsedData.namespace}/${parsedData.id}" }

        // REQ-WF-004: Validate uniqueness
        val workflowId = WorkflowID(parsedData.namespace, parsedData.id)
        if (repository.exists(workflowId)) {
            logger.warn { "Workflow already exists: $workflowId" }
            throw WorkflowAlreadyExistsException(workflowId)
        }

        // REQ-WF-006: Validate workflow data
        logger.debug { "Validating workflow data" }
        parsedData.validate()

        // REQ-WF-002, REQ-WF-003, REQ-WF-005: Create revision with defaults
        val now = Instant.now(clock)
        val revision = WorkflowRevision(
            namespace = parsedData.namespace,
            id = parsedData.id,
            version = 1, // REQ-WF-002: First revision is version 1
            name = parsedData.name,
            description = parsedData.description,
            active = parsedData.active, // REQ-WF-003: Default false
            steps = parsedData.steps, // Map rootStep to steps property
            createdAt = now, // REQ-WF-005: Set creation timestamp
            updatedAt = now  // REQ-WF-005: Set update timestamp
        )

        // REQ-WF-007: Persist with YAML source and return (repository stores YAML separately)
        logger.debug { "Persisting workflow revision: ${revision.namespace}/${revision.id}/${revision.version}" }
        val revisionWithSource = WorkflowRevisionWithSource.fromRevision(revision, yaml)
        val saved = repository.saveWithSource(revisionWithSource)
        logger.info { "Successfully created workflow revision: ${saved.toWorkflowRevisionID()}" }
        return saved.toWorkflowRevisionID()
    }
}
