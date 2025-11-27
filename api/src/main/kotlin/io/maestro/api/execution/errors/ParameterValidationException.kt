package io.maestro.api.execution.errors

import io.maestro.core.parameters.ParametersValidationResult
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when workflow input parameter validation fails.
 * Maps to 400 Bad Request with RFC 7807 problem+json format including invalid-params array.
 *
 * @property validationResult The validation result containing all parameter errors
 * @property revisionId The workflow revision ID for which validation failed
 */
class ParameterValidationException(
    val validationResult: ParametersValidationResult,
    val revisionId: WorkflowRevisionID,
    message: String = buildMessage(validationResult)
) : MaestroException(
    type = "/problems/workflow-parameter-validation-error",
    title = "Workflow Parameter Validation Failed",
    status = 400,
    message = message,
    instance = null
) {
    companion object {
        private fun buildMessage(validationResult: ParametersValidationResult): String {
            val errorCount = validationResult.errors.size
            return if (errorCount == 1) {
                "1 parameter validation error occurred"
            } else {
                "$errorCount parameter validation errors occurred"
            }
        }
    }
}
