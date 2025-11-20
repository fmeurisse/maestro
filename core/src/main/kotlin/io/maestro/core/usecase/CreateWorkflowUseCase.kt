package io.maestro.core.usecase

import io.maestro.core.exception.WorkflowAlreadyExistsException
import io.maestro.core.parser.WorkflowYamlParser
import io.maestro.core.repository.IWorkflowRevisionRepository
import io.maestro.core.validation.WorkflowValidator
import io.maestro.model.WorkflowRevision
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
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
    private val validator: WorkflowValidator,
    private val yamlParser: WorkflowYamlParser
) {

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
     * @throws WorkflowValidationException if validation or parsing fails (REQ-WF-006)
     */
    fun execute(yaml: String): WorkflowRevision {
        // Parse YAML to extract workflow data
        val parsedData = yamlParser.parseWorkflowDefinition(yaml)

        // REQ-WF-004: Validate uniqueness
        if (repository.existsByWorkflowId(parsedData.namespace, parsedData.id)) {
            throw WorkflowAlreadyExistsException(
                "Workflow with namespace '${parsedData.namespace}' and id '${parsedData.id}' already exists"
            )
        }

        // REQ-WF-006: Validate workflow data
        validator.validateWorkflowCreation(
            namespace = parsedData.namespace,
            id = parsedData.id,
            name = parsedData.name,
            description = parsedData.description,
            rootStep = parsedData.rootStep,
            yaml = yaml
        )

        // REQ-WF-002, REQ-WF-003, REQ-WF-005: Create revision with defaults
        val now = Instant.now()
        val revision = WorkflowRevision(
            namespace = parsedData.namespace,
            id = parsedData.id,
            version = 1L, // REQ-WF-002: First revision is version 1
            name = parsedData.name,
            description = parsedData.description,
            active = parsedData.active, // REQ-WF-003: Default false
            rootStep = parsedData.rootStep,
            createdAt = now, // REQ-WF-005: Set creation timestamp
            updatedAt = now  // REQ-WF-005: Set update timestamp
        )

        // REQ-WF-007: Persist with YAML source and return (repository stores YAML separately)
        return repository.save(revision, yaml)
    }
}
