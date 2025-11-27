package io.maestro.core.parameters

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.parameters.ParameterValidationError
import io.maestro.core.parameters.ParametersValidationResult
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.InvalidParameterValueException
import io.maestro.model.parameters.ParameterDefinition
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Validates workflow input parameters against a parameter schema.
 *
 * Performs validation checks:
 * - Required parameters are present
 * - No extra parameters beyond schema
 * - Parameter types match schema (with coercion)
 * - Default values are applied for optional parameters
 *
 * This validator collects all errors before returning, allowing clients to
 * present comprehensive validation feedback in a single response.
 */
@ApplicationScoped
class ParameterValidator @Inject constructor() {

    private val logger = KotlinLogging.logger {}

    /**
     * Validate parameters against schema.
     *
     * @param parameters The input parameters to validate
     * @param schema The parameter schema definitions
     * @param revisionId The workflow revision ID (for error context)
     * @return ValidationResult with errors and validated parameters
     */
    fun validate(
        parameters: Map<String, Any?>,
        schema: List<ParameterDefinition>,
        revisionId: WorkflowRevisionID
    ): ParametersValidationResult {
        val errors = mutableListOf<ParameterValidationError>()
        val validatedParameters = mutableMapOf<String, Any>()

        // Build schema map for quick lookup
        val schemaMap = schema.associateBy { it.name }

        // Check required parameters and validate provided parameters
        for (paramDef in schema) {
            val providedValue = parameters[paramDef.name]

            if (providedValue == null) {
                // Parameter not provided
                if (paramDef.required) {
                    // Required parameter missing
                    errors.add(
                        ParameterValidationError(
                            name = paramDef.name,
                            reason = "required parameter missing",
                            provided = null
                        )
                    )
                } else {
                    // Optional parameter - apply default if available
                    val defaultValue = paramDef.default
                    if (defaultValue != null) {
                        validatedParameters[paramDef.name] = defaultValue
                    }
                }
            } else {
                // Parameter provided - validate type
                try {
                    val convertedValue = paramDef.type.validateAndConvert(providedValue)
                    if (convertedValue != null) {
                        validatedParameters[paramDef.name] = convertedValue
                    }
                } catch (e: InvalidParameterValueException) {
                    errors.add(
                        ParameterValidationError(
                            name = paramDef.name,
                            reason = e.reason,
                            provided = providedValue
                        )
                    )
                }
            }
        }

        // Check for extra parameters not in schema
        for (paramName in parameters.keys) {
            if (!schemaMap.containsKey(paramName)) {
                errors.add(
                    ParameterValidationError(
                        name = paramName,
                        reason = "parameter not defined in schema",
                        provided = parameters[paramName]
                    )
                )
            }
        }

        val result = ParametersValidationResult(
            errors = errors,
            isValid = errors.isEmpty(),
            validatedParameters = validatedParameters
        )

        // Log validation failures with anonymized parameter names (no values for privacy)
        if (!result.isValid) {
            val failedParams = errors.map { it.name }
            logger.warn { "Parameter validation failed for workflow ${revisionId.namespace}/${revisionId.id}/v${revisionId.version}: ${errors.size} errors (params: ${failedParams.joinToString()})" }
        }

        return result
    }
}