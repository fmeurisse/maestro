package io.maestro.api.execution.errors

import io.maestro.api.errors.ProblemDetail
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.Instant

/**
 * Maps IllegalArgumentException to RFC 7807 Problem Details with 400 Bad Request.
 *
 * This mapper handles invalid input format errors, such as:
 * - Invalid execution ID format
 * - Invalid parameter format
 * - Other validation errors that throw IllegalArgumentException
 *
 * Response format:
 * ```json
 * {
 *   "type": "https://maestro.io/problems/bad-request",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Invalid execution ID format: must be a valid NanoID"
 * }
 * ```
 */
@Provider
class IllegalArgumentExceptionMapper : ExceptionMapper<IllegalArgumentException> {

    private val logger = KotlinLogging.logger {}

    override fun toResponse(exception: IllegalArgumentException): Response {
        logger.debug { "Mapping IllegalArgumentException to Problem Detail: ${exception.message}" }

        val problemDetail = ProblemDetail(
            type = URI.create("https://maestro.io/problems/bad-request"),
            title = "Bad Request",
            status = 400,
            detail = exception.message ?: "Invalid request format",
            timestamp = Instant.now()
        )

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}
