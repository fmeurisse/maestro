package io.maestro.model.parameters

import io.maestro.model.errors.InvalidParameterValueException

/**
 * String parameter type implementation.
 *
 * Accepts any value and converts it to a trimmed string representation.
 */
object StringParameterType : ParameterType {
    override val typeId: String = "STRING"
    override val displayName: String = "String"

    override fun validateAndConvert(value: Any?): Any? {
        if (value == null) {
            throw InvalidParameterValueException(
                expectedType = typeId,
                providedValue = value,
                reason = "value cannot be null"
            )
        }
        // String type accepts any value - convert to string
        return value.toString().trim()
    }
}
