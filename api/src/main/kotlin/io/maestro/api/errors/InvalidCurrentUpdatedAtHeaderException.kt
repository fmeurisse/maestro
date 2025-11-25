package io.maestro.api.errors

import io.maestro.model.errors.MaestroException

/**
 * Thrown when the X-Current-Updated-At header is missing or has an invalid format.
 * Maps to 400 Bad Request and is handled by the MaestroExceptionMapper to produce
 * RFC 7807 problem+json responses.
 */
class InvalidCurrentUpdatedAtHeaderException(
    /** Optional rejected header value for diagnostics */
    val rejectedValue: String? = null,
    message: String = buildMessage(rejectedValue)
) : MaestroException(
    type = "/problems/invalid-current-updated-at-header",
    title = "Invalid X-Current-Updated-At Header",
    status = 400,
    message = message,
    instance = null
) {
    companion object {
        private fun buildMessage(rejectedValue: String?): String =
            if (rejectedValue == null) {
                "X-Current-Updated-At header is required and must be an ISO-8601 instant (e.g., 2024-01-01T12:34:56Z)."
            } else {
                "Invalid X-Current-Updated-At header format: '$rejectedValue'. It must be an ISO-8601 instant (e.g., 2024-01-01T12:34:56Z)."
            }
    }
}