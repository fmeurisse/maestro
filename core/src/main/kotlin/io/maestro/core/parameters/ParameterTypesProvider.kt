package io.maestro.core.parameters

import io.maestro.model.parameters.ParameterType

/**
 * Service Provider Interface (SPI) for registering parameter types.
 *
 * Implementations of this interface can be discovered via Java ServiceLoader,
 * allowing plugins to register custom parameter types without modifying core code.
 *
 * To register a custom parameter type provider:
 * 1. Implement this interface in your plugin
 * 2. Create a file: META-INF/services/io.maestro.core.parameters.ParameterTypesProvider
 * 3. Add the fully qualified class name of your implementation to this file
 *
 * Example:
 * ```kotlin
 * class MyCustomTypesProvider : ParameterTypesProvider {
 *     override fun provide(): List<ParameterType> {
 *         return listOf(CustomEmailType, CustomUrlType)
 *     }
 * }
 * ```
 */
interface ParameterTypesProvider {
    /**
     * Provide a list of parameter types to register.
     *
     * @return List of ParameterType implementations
     */
    fun provide(): List<ParameterType>
}