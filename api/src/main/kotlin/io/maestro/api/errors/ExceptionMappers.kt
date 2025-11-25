package io.maestro.api.errors

import io.maestro.model.errors.MaestroException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.NotSupportedException
import java.net.URI
import java.time.Instant

/**
 * RFC 7807 Problem Details response structure.
 * See: https://datatracker.ietf.org/doc/html/rfc7807
 *
 * @property type A URI reference that identifies the problem type
 * @property title A short, human-readable summary of the problem type
 * @property status The HTTP status code
 * @property detail A human-readable explanation specific to this occurrence
 * @property instance A URI reference that identifies the specific occurrence
 * @property timestamp When the problem occurred (extension)
 * @property field The field that caused the problem (extension for validation errors)
 * @property rejectedValue The value that was rejected (extension for validation errors)
 */
data class ProblemDetail(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: URI? = null,
    val timestamp: Instant = Instant.now(),
    val field: String? = null,
    val rejectedValue: Any? = null
)

/**
 * Maps MaestroException (and all its subclasses) to RFC 7807 Problem Details responses.
 *
 * This mapper handles all exceptions that extend MaestroException:
 * - MalformedWorkflowIDException (400)
 * - MalformedWorkflowRevisionIDException (400)
 * - InvalidWorkflowRevision (400)
 * - InvalidWorkflowRevisionException (400)
 * - WorkflowRevisionParsingException (400)
 * - WorkflowAlreadyExistsException (409)
 * - ActiveRevisionConflictException (409)
 * - WorkflowRevisionNotFoundException (404)
 * - WorkflowNotFoundException (404)
 *
 * The exception's type, title, status, and message are used directly from the exception,
 * ensuring consistency with the RFC 7807 Problem Details format.
 */
@Provider
class MaestroExceptionMapper : ExceptionMapper<MaestroException> {

    private val logger = KotlinLogging.logger {}

    override fun toResponse(exception: MaestroException): Response {
        val statusCode = exception.status ?: Response.Status.BAD_REQUEST.statusCode
        val instanceUri = exception.instance?.let { str -> URI.create(str) }
        
        logger.debug { "Mapping MaestroException to Problem Detail: type=${exception.type}, status=$statusCode" }
        
        val problemDetail = ProblemDetail(
            type = URI.create(exception.type),
            title = exception.title,
            status = statusCode,
            detail = exception.message,
            instance = instanceUri
        )

        return Response.status(statusCode)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}

/**
 * Exception mapper for JAX-RS NotSupportedException (unsupported media type).
 * Returns 415 Unsupported Media Type when client sends wrong Content-Type.
 */
@Provider
class NotSupportedExceptionMapper : ExceptionMapper<NotSupportedException> {

    private val logger = KotlinLogging.logger {}

    override fun toResponse(exception: NotSupportedException): Response {
        logger.debug { "Unsupported media type: ${exception.message}" }

        val problemDetail = ProblemDetail(
            type = URI.create("about:blank"),
            title = "Unsupported Media Type",
            status = 415,
            detail = "The media type of the request is not supported"
        )

        return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}

/**
 * Generic exception mapper for unhandled exceptions
 */
@Provider
class GenericExceptionMapper : ExceptionMapper<Exception> {

    private val logger = KotlinLogging.logger {}

    override fun toResponse(exception: Exception): Response {
        logger.error(exception) { "Unhandled exception occurred: ${exception.message}" }

        val problemDetail = ProblemDetail(
            type = URI.create("about:blank"),
            title = "Internal Server Error",
            status = 500,
            detail = "An unexpected error occurred"
        )

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}
