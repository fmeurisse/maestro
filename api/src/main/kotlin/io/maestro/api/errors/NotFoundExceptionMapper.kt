package io.maestro.api.errors

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.net.URI

/**
 * Exception mapper for JAX-RS NotFoundException (resource not found).
 * Returns 404 Not Found when a requested resource cannot be located.
 */
@Provider
class NotFoundExceptionMapper : ExceptionMapper<NotFoundException> {

    @Context
    private lateinit var uriInfo: UriInfo

    private val logger = KotlinLogging.logger {}

    override fun toResponse(exception: NotFoundException): Response {
        val requestPath = uriInfo.requestUri.toString()

        logger.info { "Resource not found: path=$requestPath, method=${uriInfo.requestUri.path}, message=${exception.message}" }

        val problemDetail = ProblemDetail(
            type = URI.create("about:blank"),
            title = "Not Found",
            status = 404,
            detail = exception.message ?: "The requested resource was not found",
            instance = uriInfo.requestUri
        )

        return Response.status(Response.Status.NOT_FOUND)
            .entity(problemDetail)
            .type("application/problem+json")
            .build()
    }
}