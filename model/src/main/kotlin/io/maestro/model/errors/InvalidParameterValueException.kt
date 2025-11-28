package io.maestro.model.errors

/**
 * Exception thrown when a parameter value cannot be converted to the expected type.
 *
 * This exception indicates that validation or type conversion failed for a workflow
 * input parameter. It provides details about which parameter failed and why.
 *
 * @property parameterName The name of the parameter that failed conversion (optional for type-level validation)
 * @property expectedType The type identifier that was expected (e.g., "INTEGER", "STRING")
 * @property providedValue The value that failed conversion (may be null)
 * @property reason Human-readable explanation of why the conversion failed
 */
class InvalidParameterValueException(
    val parameterName: String? = null,
    val expectedType: String,
    val providedValue: Any?,
    val reason: String,
    type: String = "maestro:parameter-value-invalid",
    title: String = "Invalid Parameter Value",
    status: Int = 400
) : MaestroException(
    type = type,
    title = title,
    status = status,
    message = buildMessage(parameterName, expectedType, reason),
    instance = null,
    cause = null
) {
    companion object {
        private fun buildMessage(parameterName: String?, expectedType: String, reason: String): String {
            return if (parameterName != null) {
                "Parameter '$parameterName' of type $expectedType: $reason"
            } else {
                "Value for type $expectedType: $reason"
            }
        }
    }
}
