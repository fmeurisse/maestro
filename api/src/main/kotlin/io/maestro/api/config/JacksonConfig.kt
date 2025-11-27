package io.maestro.api.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.parameters.ParameterTypeRegistry
import io.maestro.core.parameters.registerParameterTypes
import io.maestro.core.workflows.steps.StepTypeRegistry
import io.maestro.core.workflows.steps.registerStepTypes
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.enterprise.context.ApplicationScoped
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
    fun jsonObjectMapperCustomizer(registry: ParameterTypeRegistry): ObjectMapperCustomizer {
        return ObjectMapperCustomizer { mapper ->
            // Register Kotlin and JSR310 modules
            mapper.registerModule(kotlinModule())
                .registerModule(JavaTimeModule())
                // Serialize Instant as ISO-8601 strings instead of numeric timestamps
                // This ensures PostgreSQL can parse them correctly in the trigger function
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerStepTypes()
                .registerParameterTypes(registry)

            log.info { "Jackson ObjectMapper (JSON) configured with ${StepTypeRegistry.getAllTypeNames().size} step types" }
        }
    }

}
