package io.maestro.core.parameters

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.model.parameters.ParameterType
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Registry for parameter types loaded via ServiceLoader.
 *
 * This registry discovers and manages all available parameter types by:
 * 1. Loading ParameterTypesProvider implementations via ServiceLoader
 * 2. Collecting all parameter types from providers
 * 3. Indexing them by typeId for fast lookup
 *
 * The registry is thread-safe and initialized once at application startup.
 */
@ApplicationScoped
class ParameterTypeRegistry {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var typesMap: Map<String, ParameterType>? = null

    /**
     * Initialize the registry by loading all parameter type providers.
     * This method is called automatically on first access and is thread-safe.
     */
    private fun initialize() {
        if (typesMap != null) return

        synchronized(this) {
            if (typesMap != null) return

            val types = mutableMapOf<String, ParameterType>()
            val providers = ServiceLoader.load(ParameterTypesProvider::class.java)

            var providerCount = 0
            for (provider in providers) {
                try {
                    val providedTypes = provider.provide()
                    logger.info { "Loading parameter types from ${provider::class.qualifiedName}: ${providedTypes.map { it.typeId }}" }

                    for (type in providedTypes) {
                        if (types.containsKey(type.typeId)) {
                            logger.warn { "Duplicate parameter type '${type.typeId}' from ${provider::class.qualifiedName} - skipping" }
                        } else {
                            types[type.typeId] = type
                            logger.debug { "Registered parameter type: ${type.typeId} (${type.displayName})" }
                        }
                    }
                    providerCount++
                } catch (e: Exception) {
                    logger.error(e) { "Failed to load parameter types from ${provider::class.qualifiedName}" }
                }
            }

            logger.info { "Parameter type registry initialized with ${types.size} types from $providerCount providers" }
            typesMap = types.toMap()
        }
    }

    /**
     * Get a parameter type by its type identifier.
     *
     * @param typeId The unique type identifier (e.g., "STRING", "INTEGER")
     * @return The ParameterType instance, or null if not found
     */
    fun getType(typeId: String): ParameterType? {
        if (typesMap == null) initialize()
        return typesMap?.get(typeId)
    }

    /**
     * Get all registered parameter types.
     *
     * @return Map of typeId to ParameterType
     */
    fun getAllTypes(): Map<String, ParameterType> {
        if (typesMap == null) initialize()
        return typesMap ?: emptyMap()
    }

    /**
     * Check if a parameter type is registered.
     *
     * @param typeId The unique type identifier
     * @return true if the type is registered
     */
    fun hasType(typeId: String): Boolean {
        if (typesMap == null) initialize()
        return typesMap?.containsKey(typeId) == true
    }
}


/**
 * Jackson deserializer for ParameterType.
 *
 * Deserializes typeId strings to their corresponding ParameterType instances
 * by looking them up in the ParameterTypeRegistry.
 * Example: "INTEGER" -> IntegerParameterType
 */
class ParameterTypeDeserializer(private val registry: ParameterTypeRegistry) : JsonDeserializer<ParameterType>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ParameterType {
        val typeId = p.text
        return registry.getType(typeId)
            ?: throw IllegalArgumentException("Unknown parameter type: $typeId")
    }
}

/**
 * Jackson serializer for ParameterType.
 *
 * Serializes ParameterType instances as their typeId string.
 * Example: IntegerParameterType -> "INTEGER"
 */
class ParameterTypeSerializer : JsonSerializer<ParameterType>() {
    override fun serialize(value: ParameterType, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.typeId)
    }
}

/**
 * Extension function to register ParameterType serialization support with Jackson ObjectMapper.
 *
 * This configures the ObjectMapper to:
 * - Serialize ParameterType instances as their typeId string
 * - Deserialize typeId strings back to ParameterType instances
 *
 * Usage:
 * ```kotlin
 * val mapper = ObjectMapper().apply {
 *     registerParameterTypes(registry)
 * }
 * ```
 */
fun ObjectMapper.registerParameterTypes(registry: ParameterTypeRegistry): ObjectMapper {
    val module = SimpleModule().apply {
        addSerializer(ParameterType::class.java, ParameterTypeSerializer())
        addDeserializer(ParameterType::class.java, ParameterTypeDeserializer(registry))
    }
    registerModule(module)
    return this
}

