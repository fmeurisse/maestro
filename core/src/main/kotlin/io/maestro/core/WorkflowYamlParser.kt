package io.maestro.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.maestro.core.exception.WorkflowRevisionParsingException
import io.maestro.core.steps.registerStepTypes
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
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

    private val yamlMapper = ObjectMapper(YAMLFactory().apply {
        disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        enable(YAMLGenerator.Feature.INDENT_ARRAYS)
    }).apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        registerStepTypes()
    }

    /**
     * Parses YAML workflow definition into a parsed workflow data object.
     *
     * @param yaml YAML string to parse
     * @return Parsed workflow data
     * @throws WorkflowRevisionParsingException if YAML is malformed or invalid
     */
    fun parseRevision(yaml: String): WorkflowRevision {
        try {
            // REQ-WF-056: Parse YAML into domain model
            return yamlMapper.readValue<WorkflowRevision>(yaml).validate()
        } catch (e: Exception) {
            // REQ-WF-057: Reject malformed YAML with clear error
            throw WorkflowRevisionParsingException(
                "Invalid YAML syntax: ${e.message}",
                e
            )
        }
    }

    /**
     * Serializes workflow data back to YAML.
     *
     * @param revision Object to serialize
     * @return YAML string representation
     */
    fun toYaml(revision: WorkflowRevision): String = yamlMapper.writeValueAsString(revision)

    fun toYaml(revisionID: WorkflowRevisionID): String = yamlMapper.writeValueAsString(revisionID)

}
