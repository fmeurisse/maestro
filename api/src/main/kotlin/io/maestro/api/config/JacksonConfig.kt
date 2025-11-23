package io.maestro.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.steps.StepTypeRegistry
import io.maestro.core.steps.If
import io.maestro.core.steps.LogTask
import io.maestro.core.steps.Sequence
import io.quarkus.arc.DefaultBean
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Named
import jakarta.inject.Singleton

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

    private val log = KotlinLogging.logger {}

    // Note: YAML ObjectMapper is created internally by WorkflowYamlParser in the core module,
    // so we don't need to produce it as a CDI bean here. This avoids ambiguous dependency issues.

    /**
     * Customizes the default ObjectMapper for JSON serialization used by Quarkus REST.
     * This approach avoids ambiguous dependency issues by customizing Quarkus's default mapper
     * rather than producing a competing bean.
     */
    @Singleton
    fun jsonObjectMapperCustomizer(): ObjectMapperCustomizer {
        return ObjectMapperCustomizer { mapper ->
            // Register Kotlin and JSR310 modules
            mapper.registerModule(kotlinModule())
            mapper.registerModule(JavaTimeModule())

            // Register core step types
            registerCoreStepTypes(mapper)

            // Discover and register plugin step types
            val pluginTypeCount = registerPluginStepTypes(mapper)

            log.info("Jackson ObjectMapper (JSON) configured with step types (core + $pluginTypeCount plugin types)")
        }
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
            val registeredTypes = registry.getAllTypes()
            registeredTypes.forEach { (typeName, stepClass) ->
                mapper.registerSubtypes(NamedType(stepClass.java, typeName))
                log.debug("Registered plugin step type: $typeName -> ${stepClass.qualifiedName}")
            }
            if (registeredTypes.isNotEmpty()) {
                log.info("Discovered ${registeredTypes.size} plugin step types")
            }
            registeredTypes.size
        } catch (e: Exception) {
            log.warn("Failed to discover plugin step types", e)
            // Continue without plugin types - core types are still registered
            0
        }
    }
}
