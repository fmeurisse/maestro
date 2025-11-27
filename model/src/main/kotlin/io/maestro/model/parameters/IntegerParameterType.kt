package io.maestro.model.parameters

import io.maestro.model.errors.InvalidParameterValueException

/**
 * Integer parameter type implementation.
 *
 * Accepts integer values, longs (within Int range), and numeric strings.
 * Rejects floats to prevent precision loss.
 */
object IntegerParameterType : ParameterType {
    override val typeId: String = "INTEGER"
    override val displayName: String = "Integer"

    override fun validateAndConvert(value: Any?): Any? {
        if (value == null) {
            throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "value cannot be null"
            )
        }

        return when (value) {
            is Int -> value
            is Long -> {
                if (value <= Int.MAX_VALUE && value >= Int.MIN_VALUE) {
                    value.toInt()
                } else {
                    throw InvalidParameterValueException(
                        expectedType = typeId,
                        providedValue = value,
                        reason = "integer value out of range"
                    )
                }
            }
            is String -> {
                val trimmed = value.trim()
                try {
                    trimmed.toInt()
                } catch (e: NumberFormatException) {
                    throw InvalidParameterValueException(
                        expectedType = typeId,
                        providedValue = value,
                        reason = "must be an integer"
                    )
                }
            }
            is Float, is Double -> {
                // Reject floats for integers (precision loss)
                throw InvalidParameterValueException(
                    expectedType = typeId,
                    providedValue = value,
                    reason = "must be an integer (floats not allowed)"
                )
            }
            else -> throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "must be an integer"
            )
        }
    }
}
