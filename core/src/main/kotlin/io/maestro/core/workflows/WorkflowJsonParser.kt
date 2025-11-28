package io.maestro.core.workflows

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.errors.WorkflowRevisionParsingException
import io.maestro.core.parameters.ParameterTypeRegistry
import io.maestro.core.parameters.registerParameterTypes
import io.maestro.core.workflows.steps.registerStepTypes
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Parser for JSON workflow definitions.
 * Implements REQ-WF-056, REQ-WF-057, REQ-WF-058.
 *
 * This is part of the Core layer because JSON parsing is business logic required
 * by the workflow creation use case, not just an API infrastructure concern.
 */
@ApplicationScoped
class WorkflowJsonParser @Inject constructor(
    private val parameterTypeRegistry: ParameterTypeRegistry
) {

    private val logger = KotlinLogging.logger {}

    private val jsonMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        // Serialize Instant as ISO-8601 strings instead of numeric timestamps
        // This ensures PostgreSQL can parse them correctly in the trigger function
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        registerStepTypes()
        registerParameterTypes(parameterTypeRegistry)
    }

    /**
     * Parses JSON workflow definition into a parsed workflow data object.
     *
     * @param json JSON string to parse
     * @return Parsed workflow data
     * @throws WorkflowRevisionParsingException if JSON is malformed or invalid
     */
    fun parseRevision(json: String, validate: Boolean = true): WorkflowRevision {
        logger.debug { "Parsing JSON workflow revision" }
        try {
            // REQ-WF-056: Parse JSON into domain model
            val revision = jsonMapper.readValue<WorkflowRevision>(json)
            if (validate) revision.validate()
            logger.debug { "Successfully parsed workflow revision: ${revision.namespace}/${revision.id}/${revision.version}" }
            return revision
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse JSON workflow revision: ${e.message}" }
            // REQ-WF-057: Reject malformed JSON with clear error
            throw WorkflowRevisionParsingException(
                "Invalid JSON syntax: ${e.message}",
                e
            )
        }
    }

    /**
     * Serializes workflow data back to JSON.
     *
     * @param revision Object to serialize
     * @return JSON string representation
     */
    fun toJson(revision: WorkflowRevision): String {
        logger.debug { "Serializing workflow revision to JSON: ${revision.namespace}/${revision.id}/${revision.version}" }
        return jsonMapper.writeValueAsString(revision)
    }

    fun toJson(revisionID: WorkflowRevisionID): String {
        logger.debug { "Serializing workflow revision ID to JSON: $revisionID" }
        return jsonMapper.writeValueAsString(revisionID)
    }

}
