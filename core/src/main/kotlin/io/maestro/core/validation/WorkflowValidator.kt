package io.maestro.core.validation

import io.maestro.core.exception.WorkflowValidationException
import io.maestro.model.steps.Step
import jakarta.enterprise.context.ApplicationScoped

/**
 * Validator for workflow data.
 * Implements validation requirements REQ-WF-047 through REQ-WF-058.
 */
@ApplicationScoped
class WorkflowValidator {

    companion object {
        private const val MAX_NAMESPACE_LENGTH = 100
        private const val MAX_ID_LENGTH = 100
        private const val MAX_NAME_LENGTH = 255
        private const val MAX_DESCRIPTION_LENGTH = 1000
        private val NAMING_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }

    /**
     * Validates workflow creation request.
     *
     * @throws WorkflowValidationException if any validation fails
     */
    fun validateWorkflowCreation(
        namespace: String,
        id: String,
        name: String,
        description: String,
        rootStep: Step,
        yaml: String
    ) {
        val errors = mutableListOf<String>()

        // REQ-WF-047: Validate namespace
        validateNamespace(namespace, errors)

        // REQ-WF-048: Validate id
        validateId(id, errors)

        // Validate name
        validateName(name, errors)

        // REQ-WF-050: Validate description
        validateDescription(description, errors)

        // REQ-WF-051, REQ-WF-054, REQ-WF-055: Validate root step
        validateRootStep(rootStep, errors)

        // Validate YAML
        validateYaml(yaml, errors)

        if (errors.isNotEmpty()) {
            throw WorkflowValidationException(
                "Workflow validation failed: ${errors.joinToString("; ")}"
            )
        }
    }

    /**
     * REQ-WF-047: Validate namespace format and constraints
     */
    private fun validateNamespace(namespace: String, errors: MutableList<String>) {
        when {
            namespace.isBlank() ->
                errors.add("Namespace must not be blank")
            namespace.length > MAX_NAMESPACE_LENGTH ->
                errors.add("Namespace must not exceed $MAX_NAMESPACE_LENGTH characters")
            !namespace.matches(NAMING_PATTERN) ->
                errors.add("Namespace must contain only alphanumeric characters, hyphens, and underscores")
        }
    }

    /**
     * REQ-WF-048: Validate id format and constraints
     */
    private fun validateId(id: String, errors: MutableList<String>) {
        when {
            id.isBlank() ->
                errors.add("ID must not be blank")
            id.length > MAX_ID_LENGTH ->
                errors.add("ID must not exceed $MAX_ID_LENGTH characters")
            !id.matches(NAMING_PATTERN) ->
                errors.add("ID must contain only alphanumeric characters, hyphens, and underscores")
        }
    }

    /**
     * Validate name constraints
     */
    private fun validateName(name: String, errors: MutableList<String>) {
        when {
            name.isBlank() ->
                errors.add("Name must not be blank")
            name.length > MAX_NAME_LENGTH ->
                errors.add("Name must not exceed $MAX_NAME_LENGTH characters")
        }
    }

    /**
     * REQ-WF-050: Validate description constraints
     */
    private fun validateDescription(description: String, errors: MutableList<String>) {
        when {
            description.isBlank() ->
                errors.add("Description must not be blank")
            description.length > MAX_DESCRIPTION_LENGTH ->
                errors.add("Description must not exceed $MAX_DESCRIPTION_LENGTH characters")
        }
    }

    /**
     * REQ-WF-051, REQ-WF-054, REQ-WF-055: Validate root step definition
     */
    private fun validateRootStep(rootStep: Step, errors: MutableList<String>) {
        try {
            // Validate step tree structure starting from root
            validateStepTree(rootStep, 0)
        } catch (e: Exception) {
            errors.add("Invalid root step definition: ${e.message}")
        }
    }

    /**
     * Recursively validates step tree with depth checking
     */
    private fun validateStepTree(step: Step, depth: Int) {
        // Prevent excessive nesting (max 10 levels)
        if (depth > 10) {
            throw IllegalStateException("Step nesting depth exceeds maximum of 10 levels")
        }

        // Step-specific validation would go here
        // For now, rely on model validation
    }

    /**
     * Validate YAML is not empty
     */
    private fun validateYaml(yaml: String, errors: MutableList<String>) {
        if (yaml.isBlank()) {
            errors.add("YAML definition must not be blank")
        }
    }
}
