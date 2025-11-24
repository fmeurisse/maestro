package io.maestro.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.errors.WorkflowRevisionParsingException
import io.maestro.core.steps.registerStepTypes
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
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

    private val logger = KotlinLogging.logger {}

    private val yamlMapper = ObjectMapper(YAMLFactory().apply {
        disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
        enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        disable(YAMLGenerator.Feature.INDENT_ARRAYS)
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
    fun parseRevision(yaml: String, validate: Boolean = true): WorkflowRevision {
        logger.debug { "Parsing YAML workflow revision" }
        try {
            // REQ-WF-056: Parse YAML into domain model
            val revision = yamlMapper.readValue<WorkflowRevision>(yaml)
            if (validate) revision.validate()
            logger.debug { "Successfully parsed workflow revision: ${revision.namespace}/${revision.id}/${revision.version}" }
            return revision
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse YAML workflow revision: ${e.message}" }
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
    fun toYaml(revision: WorkflowRevision): String {
        logger.debug { "Serializing workflow revision to YAML: ${revision.namespace}/${revision.id}/${revision.version}" }
        return yamlMapper.writeValueAsString(revision)
    }

    fun toYaml(revisionID: WorkflowRevisionID): String {
        logger.debug { "Serializing workflow revision ID to YAML: $revisionID" }
        return yamlMapper.writeValueAsString(revisionID)
    }

    /**
     * Serializes a list of objects to YAML.
     *
     * @param list List to serialize
     * @return YAML string representation
     */
    fun toYaml(list: List<*>): String {
        logger.debug { "Serializing list of ${list.size} items to YAML" }
        return yamlMapper.writeValueAsString(list)
    }

}
