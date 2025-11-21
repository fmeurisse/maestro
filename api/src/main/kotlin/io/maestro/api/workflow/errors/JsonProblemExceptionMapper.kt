package io.maestro.api.workflow.errors

import io.maestro.core.exception.ActiveRevisionConflictException
import io.maestro.core.exception.InvalidStepException
import io.maestro.core.exception.InvalidYamlException
import io.maestro.core.exception.ValidationException
import io.maestro.core.exception.WorkflowException
import io.maestro.core.exception.WorkflowNotFoundException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import org.zalando.problem.Problem
import org.zalando.problem.Status

/**
 * Exception mapper for WorkflowException hierarchy.
 * 
 * Maps all WorkflowException subclasses to RFC 7807 JSON Problem responses
 * using Zalando Problem library.
 * 
 * This mapper handles:
 * - WorkflowNotFoundException (404)
 * - ActiveRevisionConflictException (409)
 * - InvalidYamlException (400)
 * - InvalidStepException (400)
 * - ValidationException (400)
 * 
 * Note: WorkflowAlreadyExistsException is now handled by MaestroExceptionMapper
 * as it inherits from MaestroException.
 */
@Provider
class JsonProblemExceptionMapper : ExceptionMapper<WorkflowException> {

    private val log = Logger.getLogger(JsonProblemExceptionMapper::class.java)

    override fun toResponse(exception: WorkflowException): Response {
        log.debug("Mapping WorkflowException to JSON Problem response", exception)

        val problem = Problem.builder()
            .withType(exception.problemType)
            .withTitle(getTitle(exception))
            .withStatus(Status.valueOf(exception.status))
            .withDetail(exception.message)
            .build()

        return Response.status(exception.status)
            .entity(problem)
            .type("application/problem+json")
            .build()
    }

    /**
     * Gets a human-readable title for the exception type.
     */
    private fun getTitle(exception: WorkflowException): String {
        return when (exception) {
            is WorkflowNotFoundException -> "Workflow Not Found"
            is ActiveRevisionConflictException -> "Active Revision Conflict"
            is InvalidYamlException -> "Invalid YAML"
            is InvalidStepException -> "Invalid Step"
            is ValidationException -> "Validation Error"
        }
    }
}
