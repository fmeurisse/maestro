package io.maestro.api.config

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
    
    /**
     * Discovers all step type providers via ServiceLoader and returns
     * a registry containing all registered step types.
     * 
     * @return Registry instance with all discovered types
     */
    fun discover(): Registry {
        val registeredTypes = mutableMapOf<String, KClass<out Step>>()
        
        // Use ServiceLoader to discover plugin step type providers
        ServiceLoader.load(StepTypeProvider::class.java).forEach { provider ->
            registeredTypes.putAll(provider.provideStepTypes())
        }
        
        return Registry(registeredTypes)
    }
    
    /**
     * Creates a registry with the given step types.
     * Used for testing or explicit registration.
     */
    fun create(types: Map<String, KClass<out Step>>): Registry {
        return Registry(types)
    }
    
    /**
     * Registry instance containing discovered step types.
     */
    data class Registry(
        val registeredTypes: Map<String, KClass<out Step>>
    ) {
        /**
         * Get the KClass for a given type name.
         * 
         * @param typeName The type name (e.g., "Sequence", "LogTask")
         * @return The KClass, or null if not found
         */
        fun getStepClass(typeName: String): KClass<out Step>? {
            return registeredTypes[typeName]
        }
        
        /**
         * Check if a type name is registered.
         */
        fun isRegistered(typeName: String): Boolean {
            return registeredTypes.containsKey(typeName)
        }
        
        /**
         * Get all registered type names.
         */
        fun getAllTypeNames(): Set<String> {
            return registeredTypes.keys
        }
    }
}
