package io.maestro.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.maestro.core.exception.WorkflowValidationException
import io.maestro.model.steps.Step
import jakarta.enterprise.context.ApplicationScoped

/**
 * Parser for YAML workflow definitions.
 * Implements REQ-WF-056, REQ-WF-057, REQ-WF-058.
 *
 * This is part of the Core layer because YAML parsing is business logic required
 * by the workflow creation use case, not just an API infrastructure concern.
 */
@ApplicationScoped
class WorkflowYamlParser {

    private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }

    /**
     * Parses YAML workflow definition into a parsed workflow data object.
     *
     * @param yaml YAML string to parse
     * @return Parsed workflow data
     * @throws WorkflowValidationException if YAML is malformed or invalid
     */
    fun parseWorkflowDefinition(yaml: String): ParsedWorkflowData {
        try {
            // REQ-WF-056: Parse YAML into domain model
            return yamlMapper.readValue<ParsedWorkflowData>(yaml)
        } catch (e: Exception) {
            // REQ-WF-057: Reject malformed YAML with clear error
            throw WorkflowValidationException(
                "Invalid YAML syntax: ${e.message}",
                e
            )
        }
    }

    /**
     * Serializes workflow data back to YAML.
     *
     * @param obj Object to serialize
     * @return YAML string representation
     */
    fun toYaml(obj: Any): String {
        return yamlMapper.writeValueAsString(obj)
    }
}

/**
 * Parsed workflow data from YAML.
 * This is a Core layer model, not tied to any API framework.
 */
data class ParsedWorkflowData(
    val namespace: String,
    val id: String,
    val name: String,
    val description: String,
    val active: Boolean = false,
    val steps: List<Step>
)
