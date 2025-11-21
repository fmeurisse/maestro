package io.maestro.api.workflow.errors

import io.maestro.core.workflow.WorkflowException
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
 * - WorkflowAlreadyExistsException (409)
 * - ActiveRevisionConflictException (409)
 * - InvalidYamlException (400)
 * - InvalidStepException (400)
 * - ValidationException (400)
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
            is io.maestro.core.workflow.WorkflowNotFoundException -> "Workflow Not Found"
            is io.maestro.core.workflow.WorkflowAlreadyExistsException -> "Workflow Already Exists"
            is io.maestro.core.workflow.ActiveRevisionConflictException -> "Active Revision Conflict"
            is io.maestro.core.workflow.InvalidYamlException -> "Invalid YAML"
            is io.maestro.core.workflow.InvalidStepException -> "Invalid Step"
            is io.maestro.core.workflow.ValidationException -> "Validation Error"
        }
    }
}
