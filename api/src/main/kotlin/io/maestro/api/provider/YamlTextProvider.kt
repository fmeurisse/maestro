package io.maestro.api.provider

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.ext.MessageBodyReader
import jakarta.ws.rs.ext.MessageBodyWriter
import jakarta.ws.rs.ext.Provider
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

/**
 * JAX-RS provider for reading and writing YAML content as plain text strings.
 *
 * This provider handles both standard YAML MIME types:
 * - application/yaml
 * - application/x-yaml
 *
 * It allows JAX-RS resources to accept and produce YAML content by reading
 * and writing String parameters directly from/to the request/response body.
 */
@Provider
@Consumes("application/yaml", "application/x-yaml")
@Produces("application/yaml", "application/x-yaml")
class YamlTextProvider : MessageBodyReader<String>, MessageBodyWriter<String> {

    // MessageBodyReader implementation

    override fun isReadable(
        type: Class<*>,
        genericType: Type,
        annotations: Array<Annotation>,
        mediaType: MediaType
    ): Boolean {
        return type == String::class.java && isYamlMediaType(mediaType)
    }

    override fun readFrom(
        type: Class<String>,
        genericType: Type,
        annotations: Array<Annotation>,
        mediaType: MediaType,
        httpHeaders: MultivaluedMap<String, String>,
        entityStream: InputStream
    ): String {
        return entityStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    // MessageBodyWriter implementation

    override fun isWriteable(
        type: Class<*>,
        genericType: Type,
        annotations: Array<Annotation>,
        mediaType: MediaType
    ): Boolean {
        return type == String::class.java && isYamlMediaType(mediaType)
    }

    override fun writeTo(
        value: String,
        type: Class<*>,
        genericType: Type,
        annotations: Array<Annotation>,
        mediaType: MediaType,
        httpHeaders: MultivaluedMap<String, Any>,
        entityStream: OutputStream
    ) {
        entityStream.write(value.toByteArray(StandardCharsets.UTF_8))
    }

    // Deprecated method required by interface but not used in modern JAX-RS
    @Deprecated("Not used in modern JAX-RS implementations")
    override fun getSize(
        value: String,
        type: Class<*>,
        genericType: Type,
        annotations: Array<Annotation>,
        mediaType: MediaType
    ): Long = -1

    // Helper methods

    private fun isYamlMediaType(mediaType: MediaType): Boolean {
        return (mediaType.type == "application" &&
                (mediaType.subtype == "yaml" || mediaType.subtype == "x-yaml"))
    }
}
