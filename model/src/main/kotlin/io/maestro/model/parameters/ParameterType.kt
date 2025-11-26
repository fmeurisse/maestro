package io.maestro.model.parameters

import io.maestro.model.errors.InvalidParameterValueException

/**
 * Interface for parameter types in workflow input parameters.
 *
 * This interface allows for extensible parameter types that can be provided
 * by plugins through the ServiceLoader mechanism.
 *
 * Built-in parameter types include:
 * - STRING: Text values
 * - INTEGER: Whole numbers
 * - FLOAT: Decimal numbers
 * - BOOLEAN: True/false values
 *
 * Custom implementations can be registered via ServiceLoader by implementing
 * this interface and the ParameterTypesProvider interface.
 */
interface ParameterType {
    /**
     * Unique identifier for this parameter type.
     * Should be uppercase and URL-safe (e.g., "STRING", "INTEGER", "CUSTOM_TYPE").
     */
    val typeId: String

    /**
     * Human-readable name for this parameter type.
     */
    val displayName: String

    /**
     * Validate and convert a value to this type.
     *
     * @param value The value to validate and convert
     * @return The converted value
     * @throws InvalidParameterValueException if the value cannot be converted to this type
     */
    fun validateAndConvert(value: Any?): Any?
}
