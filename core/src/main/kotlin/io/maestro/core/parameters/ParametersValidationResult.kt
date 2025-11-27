package io.maestro.core.parameters

/**
 * Result of parameter validation.
 *
 * Contains validation errors and the validated (with defaults applied) parameter map.
 *
 * @property errors List of validation errors (empty if validation passed)
 * @property isValid True if validation passed, false otherwise
 * @property validatedParameters Map of validated parameters with defaults applied
 */
data class ParametersValidationResult(
    val errors: List<ParameterValidationError>,
    val isValid: Boolean = errors.isEmpty(),
    val validatedParameters: Map<String, Any> = emptyMap()
)