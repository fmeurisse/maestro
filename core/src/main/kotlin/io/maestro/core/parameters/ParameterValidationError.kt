package io.maestro.core.parameters

/**
 * Represents a single parameter validation error.
 *
 * @property name The parameter name that failed validation
 * @property reason Human-readable reason for the validation failure
 * @property provided The value that was provided (may be null for missing parameters)
 */
data class ParameterValidationError(
    val name: String,
    val reason: String,
    val provided: Any? = null
)