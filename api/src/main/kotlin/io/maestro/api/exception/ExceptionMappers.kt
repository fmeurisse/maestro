package io.maestro.api.exception

import io.maestro.core.exception.WorkflowAlreadyExistsException
import io.maestro.core.exception.WorkflowNotFoundException
import io.maestro.model.exception.MaestroException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
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
 * - WorkflowRevisionParsingException (400)
 * 
 * The exception's type, title, status, and message are used directly from the exception,
 * ensuring consistency with the RFC 7807 Problem Details format.
 */
@Provider
class MaestroExceptionMapper : ExceptionMapper<MaestroException> {
    override fun toResponse(exception: MaestroException): Response {
        val statusCode = exception.status ?: Response.Status.BAD_REQUEST.statusCode
        val instanceUri = exception.instance?.let { str -> URI.create(str) }
        
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
 * Maps WorkflowAlreadyExistsException to 409 Conflict
 */
@Provider
class WorkflowAlreadyExistsExceptionMapper : ExceptionMapper<WorkflowAlreadyExistsException> {
    override fun toResponse(exception: WorkflowAlreadyExistsException): Response {
        val problemDetail = ProblemDetail(
            type = URI.create("/problems/workflow-already-exists"),
            title = "Workflow Already Exists",
            status = 409,
            detail = exception.message ?: "Workflow already exists"
        )

        return Response.status(Response.Status.CONFLICT)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}

/**
 * Maps WorkflowNotFoundException to 404 Not Found
 */
@Provider
class WorkflowNotFoundExceptionMapper : ExceptionMapper<WorkflowNotFoundException> {
    override fun toResponse(exception: WorkflowNotFoundException): Response {
        val problemDetail = ProblemDetail(
            type = URI.create("/problems/workflow-not-found"),
            title = "Workflow Not Found",
            status = 404,
            detail = exception.message ?: "Workflow not found"
        )

        return Response.status(Response.Status.NOT_FOUND)
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
    override fun toResponse(exception: Exception): Response {
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
