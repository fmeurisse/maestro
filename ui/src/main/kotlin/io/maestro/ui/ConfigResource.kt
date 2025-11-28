package io.maestro.ui

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * REST endpoint that serves runtime configuration as JavaScript.
 *
 * This endpoint generates a config.js file that sets window.__RUNTIME_CONFIG__
 * with values from environment variables, allowing the frontend to be configured
 * at deployment time rather than build time.
 */
@Path("/config.js")
class ConfigResource {

    @ConfigProperty(name = "maestro.ui.api.url", defaultValue = "")
    lateinit var apiUrl: String

    @GET
    @Produces("application/javascript")
    fun getConfig(): String {
        // Generate JavaScript that sets the runtime configuration
        return """
            window.__RUNTIME_CONFIG__ = {
              apiUrl: '${escapeJavaScript(apiUrl)}'
            };
        """.trimIndent()
    }

    /**
     * Escapes a string for safe inclusion in JavaScript
     */
    private fun escapeJavaScript(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
