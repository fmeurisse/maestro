package io.maestro.model.parameters

import io.maestro.model.errors.InvalidParameterValueException

/**
 * Boolean parameter type implementation.
 *
 * Accepts boolean values and "true"/"false" strings (case-insensitive).
 * Rejects integers to avoid ambiguous conversions.
 */
object BooleanParameterType : ParameterType {
    override val typeId: String = "BOOLEAN"
    override val displayName: String = "Boolean"

    override fun validateAndConvert(value: Any?): Any? {
        if (value == null) {
            throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "value cannot be null"
            )
        }

        return when (value) {
            is Boolean -> value
            is String -> {
                val trimmed = value.trim().lowercase()
                when (trimmed) {
                    "true" -> true
                    "false" -> false
                    else -> throw InvalidParameterValueException(
                        expectedType = typeId,
                        providedValue = value,
                        reason = "must be a boolean (true or false)"
                    )
                }
            }
            is Int, is Long -> {
                // Reject integers for booleans (ambiguous)
                throw InvalidParameterValueException(
                    expectedType = typeId,
                    providedValue = value,
                    reason = "must be a boolean (integers not allowed)"
                )
            }
            else -> throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "must be a boolean"
            )
        }
    }
}
