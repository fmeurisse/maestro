package io.maestro.plugins.postgres.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import jakarta.inject.Named
import jakarta.inject.Inject
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.core.kotlin.KotlinPlugin
import javax.sql.DataSource

/**
 * Database configuration for JDBI.
 * 
 * Provides JDBI instance configured with:
 * - PostgreSQL plugin for PostgreSQL-specific features (JSONB support)
 * - Kotlin plugin for Kotlin data class mapping
 * - Jackson ObjectMapper for JSONB serialization/deserialization
 * 
 * The DataSource is provided by the application (e.g., Quarkus from application.yml).
 * This class uses CDI to inject the DataSource.
 */
@ApplicationScoped
class DatabaseConfig @Inject constructor(
    private val dataSource: DataSource
) {

    /**
     * Produces a configured ObjectMapper for JSONB serialization.
     * Used by repository to serialize/deserialize WorkflowRevision to/from JSONB.
     */
    @Produces
    @Singleton
    @Named("jsonbObjectMapper")
    fun jsonbObjectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())
    }

    /**
     * Produces a configured JDBI instance.
     * 
     * @return Configured JDBI instance
     */
    @Produces
    @Singleton
    fun jdbi(): Jdbi {
        return Jdbi.create(dataSource)
            .installPlugin(PostgresPlugin())
            .installPlugin(KotlinPlugin())
    }
}
