package io.maestro.core.steps

import io.maestro.model.steps.Step
import kotlin.reflect.KClass
import java.util.ServiceLoader

/**
 * Registry for discovering and managing step types at runtime.
 * 
 * Uses the ServiceLoader pattern to discover StepTypeProvider implementations
 * from plugins. Core step types are registered explicitly, while plugin
 * step types are discovered automatically.
 * 
 * This enables plugin extensibility without recompiling core code.
 */
object StepTypeRegistry {

    private val registeredTypes: MutableMap<String, KClass<out Step>> = mutableMapOf()

    init {
        discover()
    }

    /**
     * Discovers all step type providers via ServiceLoader and returns
     * a registry containing all registered step types.
     * 
     * @return Registry instance with all discovered types
     */
    fun discover(): StepTypeRegistry {
        
        // Use ServiceLoader to discover plugin step type providers
        ServiceLoader.load(StepTypesProvider::class.java).forEach { provider ->
            registeredTypes.putAll(provider.provideStepTypes())
        }
        
        return this
    }
    
    /**
     * Creates a registry with the given step types.
     * Used for testing or explicit registration.
     */
    fun registerTypes(types: Map<String, KClass<out Step>>): StepTypeRegistry {
        registeredTypes.putAll(types)
        return this
    }

    /**
     * Get the KClass for a given type name.
     *
     * @param typeName The type name (e.g., "Sequence", "LogTask")
     * @return The KClass, or null if not found
     */
    fun getStepClass(typeName: String): KClass<out Step>? = registeredTypes[typeName]

    /**
     * Check if a type name is registered.
     */
    fun isRegistered(typeName: String): Boolean = registeredTypes.containsKey(typeName)

    /**
     * Get all registered type names.
     */
    fun getAllTypeNames(): Set<String> = registeredTypes.keys

    /**
     * Get all registered types.
     */
    fun getAllTypes(): Map<String, KClass<out Step>> = registeredTypes.toMap()

}
