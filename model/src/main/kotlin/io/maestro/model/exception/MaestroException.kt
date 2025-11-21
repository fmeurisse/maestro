package io.maestro.model.exception

/**
 * Abstract root exception for all Maestro errors.
 * Implements RFC 7807 Problem Details for HTTP APIs structure.
 *
 * This exception provides a standardized way to represent errors that can be
 * serialized to JSON Problem format (application/problem+json).
 *
 * @property type URI reference that identifies the problem type (RFC 7807).
 *               Defaults to "about:blank" if not specified.
 * @property title Short, human-readable summary of the problem type.
 * @property status HTTP status code applicable to this problem (optional).
 * @property detail Human-readable explanation specific to this occurrence of the problem.
 * @property instance URI reference that identifies the specific occurrence of the problem (optional).
 *
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807 - Problem Details for HTTP APIs</a>
 */
abstract class MaestroException(
    /**
     * URI reference that identifies the problem type.
     * Defaults to "about:blank" if not specified.
     */
    open val type: String = "about:blank",
    
    /**
     * Short, human-readable summary of the problem type.
     */
    open val title: String,
    
    /**
     * HTTP status code applicable to this problem.
     * Optional, as some exceptions may not map directly to HTTP status codes.
     */
    open val status: Int? = null,
    
    /**
     * Human-readable explanation specific to this occurrence of the problem.
     * This is the main error message.
     */
    override val message: String,
    
    /**
     * URI reference that identifies the specific occurrence of the problem.
     * Optional, useful for tracking specific error instances.
     */
    open val instance: String? = null,
    
    /**
     * Optional cause exception.
     */
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    /**
     * Convenience constructor that uses the message as both title and detail.
     * Useful for simple exceptions.
     */
    constructor(
        type: String = "about:blank",
        message: String,
        status: Int? = null,
        instance: String? = null,
        cause: Throwable? = null
    ) : this(type, message, status, message, instance, cause)
}
