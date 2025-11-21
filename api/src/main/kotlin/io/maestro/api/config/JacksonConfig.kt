package io.maestro.api.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.maestro.model.steps.If
import io.maestro.model.steps.LogTask
import io.maestro.model.steps.Sequence
import io.maestro.model.steps.Step
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.jboss.logging.Logger

/**
 * Jackson ObjectMapper configuration for YAML/JSON serialization.
 * 
 * Configures polymorphic type handling for Step hierarchy with runtime
 * step type registration via ServiceLoader for plugin extensibility.
 * 
 * This configuration:
 * - Registers core step types (Sequence, If, LogTask)
 * - Discovers plugin step types via StepTypeRegistry
 * - Configures YAML factory with proper formatting
 * - Enables polymorphic type serialization with "type" property
 * 
 * Note: For sealed interfaces in Kotlin, Jackson requires @JsonTypeInfo annotation
 * on the Step interface. However, subtype registration happens at runtime via
 * this configuration class, enabling plugin extensibility.
 */
@ApplicationScoped
class JacksonConfig {

    private val log = Logger.getLogger(JacksonConfig::class.java)

    /**
     * Produces a configured ObjectMapper for YAML serialization.
     * 
     * The mapper is configured with:
     * - Kotlin module for data class support
     * - JavaTimeModule for Instant serialization
     * - YAML factory with proper formatting
     * - Runtime-registered step types for polymorphism
     */
    @Produces
    @Singleton
    fun yamlObjectMapper(): ObjectMapper {
        val mapper = ObjectMapper(
            YAMLFactory().apply {
                disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                enable(YAMLGenerator.Feature.INDENT_ARRAYS)
            }
        )
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())

        // Register core step types
        registerCoreStepTypes(mapper)

        // Discover and register plugin step types
        val pluginTypeCount = registerPluginStepTypes(mapper)

        log.info("Jackson ObjectMapper (YAML) configured with step types (core + $pluginTypeCount plugin types)")


        return mapper
    }

    /**
     * Produces a configured ObjectMapper for JSON serialization.
     * Same configuration as YAML mapper but without YAML factory.
     */
    @Produces
    @Singleton
    fun jsonObjectMapper(): ObjectMapper {
        val mapper = ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())

        // Register core step types
        registerCoreStepTypes(mapper)

        // Discover and register plugin step types
        val pluginTypeCount = registerPluginStepTypes(mapper)

        log.info("Jackson ObjectMapper (JSON) configured with step types (core + $pluginTypeCount plugin types)")

        return mapper
    }

    /**
     * Registers core step types that are part of the model module.
     * 
     * Note: WorkTask is mentioned in the spec but may not exist yet.
     * Add it here when it's created.
     */
    private fun registerCoreStepTypes(mapper: ObjectMapper) {
        mapper.registerSubtypes(
            NamedType(Sequence::class.java, "Sequence"),
            NamedType(If::class.java, "If"),
            NamedType(LogTask::class.java, "LogTask")
            // WorkTask will be added when it's created in the model module
        )
        log.debug("Registered core step types: Sequence, If, LogTask")
    }

    /**
     * Discovers and registers plugin step types via ServiceLoader.
     * 
     * @return Number of plugin step types registered
     */
    private fun registerPluginStepTypes(mapper: ObjectMapper): Int {
        return try {
            val registry = StepTypeRegistry.discover()
            registry.registeredTypes.forEach { (typeName, stepClass) ->
                mapper.registerSubtypes(NamedType(stepClass.java, typeName))
                log.debug("Registered plugin step type: $typeName -> ${stepClass.qualifiedName}")
            }
            if (registry.registeredTypes.isNotEmpty()) {
                log.info("Discovered ${registry.registeredTypes.size} plugin step types")
            }
            registry.registeredTypes.size
        } catch (e: Exception) {
            log.warn("Failed to discover plugin step types", e)
            // Continue without plugin types - core types are still registered
            0
        }
    }
}
