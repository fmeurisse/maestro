package io.maestro.model.parameters

import io.maestro.model.errors.InvalidParameterValueException

/**
 * Float parameter type implementation.
 *
 * Accepts float/double values, integers, and numeric strings.
 * Automatically coerces integers to floats.
 */
object FloatParameterType : ParameterType {
    override val typeId: String = "FLOAT"
    override val displayName: String = "Float"

    override fun validateAndConvert(value: Any?): Any? {
        if (value == null) {
            throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "value cannot be null"
            )
        }

        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is String -> {
                val trimmed = value.trim()
                try {
                    trimmed.toFloat()
                } catch (e: NumberFormatException) {
                    throw InvalidParameterValueException(
                        expectedType = typeId,
                        providedValue = value,
                        reason = "must be a float"
                    )
                }
            }
            else -> throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "must be a float"
            )
        }
    }
}
