package io.maestro.api.execution.errors

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.Instant

/**
 * Maps ParameterValidationException to RFC 7807 Problem Details with invalid-params extension.
 *
 * This mapper extends the standard Problem Detail format with an `invalidParams` array
 * containing detailed validation errors for each parameter that failed validation.
 *
 * Response format:
 * ```json
 * {
 *   "type": "https://maestro.io/problems/workflow-parameter-validation-error",
 *   "title": "Workflow Parameter Validation Failed",
 *   "status": 400,
 *   "detail": "2 parameter validation errors occurred",
 *   "instance": "/api/workflows/production/payment-processing/3/execute",
 *   "timestamp": "2025-11-25T10:30:00Z",
 *   "invalidParams": [
 *     {
 *       "name": "retryCount",
 *       "reason": "must be a positive integer",
 *       "provided": "not-a-number"
 *     },
 *     {
 *       "name": "userName",
 *       "reason": "required parameter missing",
 *       "provided": null
 *     }
 *   ]
 * }
 * ```
 */
@Provider
class ParameterValidationExceptionMapper : ExceptionMapper<ParameterValidationException> {

    private val logger = KotlinLogging.logger {}

    /**
     * Data class for invalid parameter entry in RFC 7807 invalid-params extension.
     */
    data class InvalidParam(
        val name: String,
        val reason: String,
        val provided: Any?
    )

    /**
     * Extended Problem Detail with invalid-params array.
     */
    data class ParameterValidationProblemDetail(
        val type: URI,
        val title: String,
        val status: Int,
        val detail: String,
        val instance: URI? = null,
        val timestamp: Instant = Instant.now(),
        val invalidParams: List<InvalidParam>
    )

    override fun toResponse(exception: ParameterValidationException): Response {
        val statusCode = exception.status ?: Response.Status.BAD_REQUEST.statusCode
        val instanceUri = exception.instance?.let { str -> URI.create(str) }

        logger.debug {
            "Mapping ParameterValidationException to Problem Detail: " +
            "type=${exception.type}, status=$statusCode, errors=${exception.validationResult.errors.size}"
        }

        // Convert validation errors to invalid-params format
        val invalidParams = exception.validationResult.errors.map { error ->
            InvalidParam(
                name = error.name,
                reason = error.reason,
                provided = error.provided
            )
        }

        val problemDetail = ParameterValidationProblemDetail(
            type = URI.create(exception.type),
            title = exception.title,
            status = statusCode,
            detail = exception.message,
            instance = instanceUri,
            invalidParams = invalidParams
        )

        return Response.status(statusCode)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}
