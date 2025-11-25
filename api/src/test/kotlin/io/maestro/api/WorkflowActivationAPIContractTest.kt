package io.maestro.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract tests for workflow revision activation/deactivation endpoints.
 *
 * Tests cover:
 * - Activating inactive revisions (POST /{namespace}/{id}/{version}/activate)
 * - Deactivating active revisions (POST /{namespace}/{id}/{version}/deactivate)
 * - Multi-active revision support
 * - Filtering revisions by active status (GET /{namespace}/{id}?active=true)
 * - Error cases (404 Not Found, etc.)
 */
@QuarkusTest
class WorkflowActivationAPIContractTest {

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

        private fun createWorkflowYaml(namespace: String, id: String, message: String = "Test message"): String {
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
            return updatedAtMatch?.groupValues?.get(1) ?: throw AssertionError("Could not find updatedAt in existing revision")
        }
    }

    // ===== POST /{namespace}/{id}/{version}/activate Tests =====

    @Test
    fun `should activate an inactive revision`() {
        val namespace = "test-ns-activate"
        val id = "workflow-activate"
        val yaml = createWorkflowYaml(namespace, id)

        // Create workflow (starts as inactive by default)
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Activate the revision
        val updatedAt = getExistingUpdatedAt(namespace, id, 1)
        val response = RestAssured.given()
            .header("X-Current-Updated-At", updatedAt)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .body(CoreMatchers.containsString("namespace:"))
            .body(CoreMatchers.containsString("id:"))
            .body(CoreMatchers.containsString("version:"))
            .extract()
            .body()
            .asString()

        // Verify the revision exists and is now referenced
        assert(response.contains(namespace)) { "Response should contain namespace" }
        assert(response.contains(id)) { "Response should contain id" }
    }

    @Test
    fun `should return 400 when activating without header`() {
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1/activate")
        .then()
            .statusCode(400)
    }

    @Test
    fun `should return 404 when activating non-existent revision`() {
        RestAssured.given()
            .header("X-Current-Updated-At", "2024-01-01T00:00:00Z")
        .`when`()
            .post("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1/activate")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-revision-not-found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should support multiple active revisions for the same workflow`() {
        val namespace = "test-ns-multi-active"
        val id = "workflow-multi-active"

        // Create three revisions
        for (version in 1..3) {
            val yaml = createWorkflowYaml(namespace, id, "Version $version")
            if (version == 1) {
                RestAssured.given()
                    .config(RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                    ))
                    .contentType("application/yaml")
                    .body(yaml)
                .`when`()
                    .post(WORKFLOW_ENDPOINT)
                .then()
                    .statusCode(201)
            } else {
                RestAssured.given()
                    .config(RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                    ))
                    .contentType("application/yaml")
                    .body(yaml)
                .`when`()
                    .post("$WORKFLOW_ENDPOINT/$namespace/$id")
                .then()
                    .statusCode(201)
            }
        }

        // Activate versions 1 and 3 (leaving 2 inactive)
        val updatedAtV1 = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtV1)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        val updatedAtV3 = getExistingUpdatedAt(namespace, id, 3)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtV3)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/3/activate")
        .then()
            .statusCode(200)

        // List all revisions - should show 3 total
        val allRevisionsResponse = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Verify we have 3 revisions total
        val versionMatches = Regex("version: (\\d+)").findAll(allRevisionsResponse)
        val versionCount = versionMatches.count()
        assert(versionCount == 3) { "Should have 3 total revisions, found $versionCount" }

        // List only active revisions - should show 2
        val activeRevisionsResponse = RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Verify we have 2 active revisions
        val activeVersionMatches = Regex("version: (\\d+)").findAll(activeRevisionsResponse)
        val activeVersionCount = activeVersionMatches.count()
        assert(activeVersionCount == 2) { "Should have 2 active revisions, found $activeVersionCount" }
    }

    // ===== POST /{namespace}/{id}/{version}/deactivate Tests =====

    @Test
    fun `should deactivate an active revision`() {
        val namespace = "test-ns-deactivate"
        val id = "workflow-deactivate"
        val yaml = createWorkflowYaml(namespace, id)

        // Create and activate a workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        val updatedAtBeforeActivate = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeActivate)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Deactivate the revision
        val updatedAtBeforeDeactivate = getExistingUpdatedAt(namespace, id, 1)
        val response = RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeDeactivate)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/deactivate")
        .then()
            .statusCode(200)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .extract()
            .body()
            .asString()

        // Verify the revision is referenced in response
        assert(response.contains(namespace)) { "Response should contain namespace" }
        assert(response.contains(id)) { "Response should contain id" }
    }

    @Test
    fun `should return 400 when deactivating without header`() {
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1/deactivate")
        .then()
            .statusCode(400)
    }

    @Test
    fun `should return 404 when deactivating non-existent revision`() {
        RestAssured.given()
            .header("X-Current-Updated-At", "2024-01-01T00:00:00Z")
        .`when`()
            .post("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1/deactivate")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-revision-not-found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    // ===== GET /{namespace}/{id}?active=true Tests =====

    @Test
    fun `should filter revisions by active status using query parameter`() {
        val namespace = "test-ns-filter"
        val id = "workflow-filter"

        // Create 3 revisions
        for (version in 1..3) {
            val yaml = createWorkflowYaml(namespace, id, "Version $version")
            if (version == 1) {
                RestAssured.given()
                    .config(RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                    ))
                    .contentType("application/yaml")
                    .body(yaml)
                .`when`()
                    .post(WORKFLOW_ENDPOINT)
                .then()
                    .statusCode(201)
            } else {
                RestAssured.given()
                    .config(RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                    ))
                    .contentType("application/yaml")
                    .body(yaml)
                .`when`()
                    .post("$WORKFLOW_ENDPOINT/$namespace/$id")
                .then()
                    .statusCode(201)
            }
        }

        // Activate only version 2
        val updatedAtV2 = getExistingUpdatedAt(namespace, id, 2)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtV2)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/2/activate")
        .then()
            .statusCode(200)

        // Get all revisions (without filter)
        val allRevisionsResponse = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Should return all 3 revisions
        val allVersions = Regex("version: (\\d+)").findAll(allRevisionsResponse)
        assert(allVersions.count() == 3) { "Should return 3 revisions without filter" }

        // Get only active revisions (with filter)
        val activeRevisionsResponse = RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Should return only 1 active revision
        val activeVersions = Regex("version: (\\d+)").findAll(activeRevisionsResponse)
        assert(activeVersions.count() == 1) { "Should return 1 active revision with filter" }

        // Verify it's version 2
        assert(activeRevisionsResponse.contains("version: 2") || activeRevisionsResponse.contains("version:2")) {
            "Active revision should be version 2"
        }
    }

    @Test
    fun `should return 404 when filtering for active revisions but none exist`() {
        val namespace = "test-ns-no-active"
        val id = "workflow-no-active"
        val yaml = createWorkflowYaml(namespace, id)

        // Create a workflow (inactive by default)
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Try to get active revisions (should be none)
        RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(404) // No active revisions found
    }

    @Test
    fun `should support activation and deactivation lifecycle`() {
        val namespace = "test-ns-lifecycle"
        val id = "workflow-lifecycle"
        val yaml = createWorkflowYaml(namespace, id)

        // Create workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Step 1: Activate revision
        val updatedAtBeforeActivate = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeActivate)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Verify it's active
        val activeCheck1 = RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        assert(activeCheck1.contains("version: 1") || activeCheck1.contains("version:1")) {
            "Version 1 should be active"
        }

        // Step 2: Deactivate revision
        val updatedAtBeforeDeactivate = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeDeactivate)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/deactivate")
        .then()
            .statusCode(200)

        // Verify no active revisions
        RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(404) // No active revisions

        // Step 3: Reactivate
        val updatedAtBeforeReactivate = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeReactivate)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Verify it's active again
        RestAssured.given()
            .queryParam("active", "true")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
    }
}
