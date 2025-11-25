package io.maestro.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.http.ContentType
import io.restassured.parsing.Parser
import jakarta.inject.Inject
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for concurrent workflow revision operations.
 *
 * Tests cover non-conflicting concurrent operations where multiple users
 * perform independent actions simultaneously.
 *
 * Scenarios tested:
 * - Concurrent creation of different revisions (should succeed)
 * - Concurrent activation/deactivation operations (multi-active allowed)
 *
 * Note: Optimistic locking tests for concurrent updates have been moved
 * to WorkflowUpdateAPIContractTest.
 */
@QuarkusTest
class WorkflowConcurrencyAPIContractTest {

    @Inject
    lateinit var jdbi: Jdbi

    @BeforeEach
    fun cleanupDatabase() {
        // Delete all workflow revisions before each test to ensure test isolation
        jdbi.useHandle<Exception> { handle ->
            handle.execute("DELETE FROM workflow_revisions")
        }
    }

    companion object {
        private const val WORKFLOW_ENDPOINT = "/api/workflows"

        private fun createWorkflowYaml(
            namespace: String,
            id: String,
            message: String = "Test message"
        ): String {
            return """
                namespace: $namespace
                id: $id
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: "$message"
            """.trimIndent()
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            RestAssured.registerParser("application/x-yaml", Parser.TEXT);
            RestAssured.registerParser("application/yaml", Parser.TEXT);
        }

        /**
         * Fetches the existing revision and extracts its updatedAt for optimistic locking.
         */
        private fun getExistingUpdatedAt(namespace: String, id: String, version: Int): String {
            val existingRevisionYaml = RestAssured.given()
                .accept("application/yaml")
                .`when`()
                .get("$WORKFLOW_ENDPOINT/$namespace/$id/$version")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString()

            val updatedAtRegex = Regex("""updatedAt:\s*([^\s\n]+)""")
            val updatedAtMatch = updatedAtRegex.find(existingRevisionYaml)
            return updatedAtMatch?.groupValues?.get(1)
                ?: throw AssertionError("Could not find updatedAt in existing revision")
        }
    }

    // ===== Concurrent Operation Tests =====

    @Test
    fun `should allow concurrent creation of different revisions`() {
        val namespace = "multi-revision-ns"
        val id = "workflow-multi"

        // Create initial workflow (version 1)
        val v1Yaml = createWorkflowYaml(namespace, id, message = "Version 1")
        RestAssured.given()
            .config(
                RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                )
            )
            .contentType("application/yaml")
            .body(v1Yaml)
            .`when`()
            .post(WORKFLOW_ENDPOINT)
            .then()
            .statusCode(201)

        // Create version 2 and version 3 "concurrently" (independent operations)
        val v2Yaml = createWorkflowYaml(namespace, id, message = "Version 2")
        val createV2 = RestAssured.given()
            .config(
                RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                )
            )
            .contentType("application/yaml")
            .accept("application/yaml")
            .body(v2Yaml)
            .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
            .statusCode(201)
            .extract()
            .response()

        val v3Yaml = createWorkflowYaml(namespace, id, message = "Version 3")
        val createV3 = RestAssured.given()
            .config(
                RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                )
            )
            .contentType("application/yaml")
            .accept("application/yaml")
            .body(v3Yaml)
            .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
            .statusCode(201)
            .extract()
            .response()

        // Both should succeed - creating different revisions is not a conflict
        // Extract version from YAML response (API only returns YAML)
        val v2YamlResponse = createV2.body().asString()
        val v3YamlResponse = createV3.body().asString()
        val v2VersionRegex = Regex("""version:\s*(\d+)""")
        val v3VersionRegex = Regex("""version:\s*(\d+)""")
        val v2Version = v2VersionRegex.find(v2YamlResponse)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not find version in v2 response")
        val v3Version = v3VersionRegex.find(v3YamlResponse)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("Could not find version in v3 response")
        assert(v2Version == 2)
        assert(v3Version == 3)

        // Verify all three revisions exist
        val allRevisionsYaml = RestAssured.given()
            .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Check that YAML contains all 3 versions
        assert(allRevisionsYaml.contains("version: 1") || allRevisionsYaml.contains("version:1")) { "Should contain version 1" }
        assert(allRevisionsYaml.contains("version: 2") || allRevisionsYaml.contains("version:2")) { "Should contain version 2" }
        assert(allRevisionsYaml.contains("version: 3") || allRevisionsYaml.contains("version:3")) { "Should contain version 3" }
    }

    @Test
    fun `should handle concurrent activation without conflicts`() {
        val namespace = "activation-ns"
        val id = "workflow-activation"

        // Create two revisions
        val v1Yaml = createWorkflowYaml(namespace, id, message = "Version 1")
        RestAssured.given()
            .config(
                RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                )
            )
            .contentType("application/yaml")
            .accept("application/yaml")
            .body(v1Yaml)
            .`when`()
            .post(WORKFLOW_ENDPOINT)
            .then()
            .statusCode(201)

        val v2Yaml = createWorkflowYaml(namespace, id, message = "Version 2")
        RestAssured.given()
            .config(
                RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                )
            )
            .contentType("application/yaml")
            .accept("application/yaml")
            .body(v2Yaml)
            .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
            .statusCode(201)

        // Activate both revisions "concurrently" (both should succeed - multi-active allowed)
        val updatedAtV1 = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtV1)
            .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
            .then()
            .statusCode(200)

        val updatedAtV2 = getExistingUpdatedAt(namespace, id, 2)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtV2)
            .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/2/activate")
            .then()
            .statusCode(200)

        // Verify both are active
        // API only returns YAML, so parse YAML response to count active revisions
        val activeRevisionsYaml = RestAssured.given()
            .accept("application/yaml")
            .queryParam("active", "true")
            .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Count the number of "version:" occurrences in the YAML (each revision has one)
        val versionCount = Regex("""version:\s*\d+""").findAll(activeRevisionsYaml).count()
        assert(versionCount == 2) {
            "Both revisions should be active"
        }
    }
}
