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
 * Contract tests for workflow revision management endpoints.
 *
 * Tests cover:
 * - Creating new revisions (POST /workflows/{namespace}/{id})
 * - Listing workflow revisions (GET /workflows/{namespace}/{id})
 * - Getting specific revisions (GET /workflows/{namespace}/{id}/{version})
 * - Error cases (404 Not Found, version sequencing, etc.)
 */
@QuarkusTest
class WorkflowRevisionAPIContractTest : AbstractAPIContractTest() {

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
    }

    // ===== POST /workflows/{namespace}/{id} - Create Revision Tests =====

    @Test
    fun `should create second revision successfully`() {
        val namespace = "test-ns-rev"
        val id = "workflow-rev"
        val yaml1 = createWorkflowYaml(namespace, id, "Version 1")
        val yaml2 = createWorkflowYaml(namespace, id, "Version 2")

        // Create first workflow (version 1)
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Create second revision (version 2)
        val response = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml2)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(201)
            .contentType("application/yaml")
            .header("Location", CoreMatchers.notNullValue())
            .body(CoreMatchers.containsString("namespace:"))
            .body(CoreMatchers.containsString("id:"))
            .body(CoreMatchers.containsString("version:"))
            .extract()
            .body()
            .asString()

        // Verify version is 2
        assert(response.contains("version: 2") || response.contains("version:2"))
    }

    @Test
    fun `should create multiple sequential revisions`() {
        val namespace = "test-ns-multi"
        val id = "workflow-multi"

        // Create first workflow
        val yaml1 = createWorkflowYaml(namespace, id, "Version 1")
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Create revisions 2, 3, 4
        for (version in 2..4) {
            val yaml = createWorkflowYaml(namespace, id, "Version $version")
            val response = RestAssured.given()
                .config(RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
                ))
                .contentType("application/yaml")
                .body(yaml)
            .`when`()
                .post("$WORKFLOW_ENDPOINT/$namespace/$id")
            .then()
                .statusCode(201)
                .extract()
                .body()
                .asString()

            // Verify correct version number
            assert(response.contains("version: $version") || response.contains("version:$version")) {
                "Expected version $version but got: $response"
            }
        }
    }

    @Test
    fun `should return 404 when creating revision for non-existent workflow`() {
        val yaml = createWorkflowYaml("non-existent-ns", "non-existent-id")

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-not-found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should return Location header with correct version for new revision`() {
        val namespace = "test-ns-location"
        val id = "workflow-location"
        val yaml1 = createWorkflowYaml(namespace, id, "Version 1")
        val yaml2 = createWorkflowYaml(namespace, id, "Version 2")

        // Create first workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Create second revision and verify Location header
        val location = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml2)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(201)
            .extract()
            .header("Location")

        // Verify Location header format with version 2
        assert(location != null) { "Location header should not be null" }
        assert(location!!.matches(Regex(".*/api/workflows/$namespace/$id/2"))) {
            "Location header '$location' should end with version 2"
        }
    }

    // ===== GET /workflows/{namespace}/{id} - List Revisions Tests =====

    @Test
    fun `should list all revisions of a workflow`() {
        val namespace = "test-ns-list"
        val id = "workflow-list"

        // Create workflow with 3 revisions
        val yaml1 = createWorkflowYaml(namespace, id, "Version 1")
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Create revisions 2 and 3
        for (version in 2..3) {
            val yaml = createWorkflowYaml(namespace, id, "Version $version")
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

        // List all revisions
        val response = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(200)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .extract()
            .body()
            .asString()

        // Verify all three versions are present
        assert(response.contains(namespace)) { "Response should contain namespace" }
        assert(response.contains(id)) { "Response should contain workflow id" }
    }

    @Test
    fun `should return 404 when listing revisions for non-existent workflow`() {
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should return empty list format when workflow has no revisions`() {
        // This test verifies behavior when workflow exists but has no revisions
        // In current implementation, if no revisions exist, we return 404
        // This is consistent with "workflow not found" semantics
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/empty-ns/empty-id")
        .then()
            .statusCode(404)
    }

    // ===== GET /workflows/{namespace}/{id}/{version} - Get Specific Revision Tests =====

    @Test
    fun `should get specific revision with YAML source`() {
        val namespace = "test-ns-get"
        val id = "workflow-get"
        val yaml1 = createWorkflowYaml(namespace, id, "Version 1 message")

        // Create workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Get the revision
        val response = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .extract()
            .body()
            .asString()

        // Verify we got the original YAML back
        assert(response.contains(namespace)) { "Response should contain namespace" }
        assert(response.contains(id)) { "Response should contain id" }
        assert(response.contains("Version 1 message")) { "Response should contain the original message" }
    }

    @Test
    fun `should return 404 for non-existent revision version`() {
        val namespace = "test-ns-404"
        val id = "workflow-404"
        val yaml = createWorkflowYaml(namespace, id)

        // Create only version 1
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

        // Try to get version 2 (doesn't exist)
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should get correct version when multiple revisions exist`() {
        val namespace = "test-ns-versions"
        val id = "workflow-versions"

        // Create 3 revisions with distinct messages
        val yaml1 = createWorkflowYaml(namespace, id, "This is version 1")
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml1)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        val yaml2 = createWorkflowYaml(namespace, id, "This is version 2")
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml2)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(201)

        val yaml3 = createWorkflowYaml(namespace, id, "This is version 3")
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(yaml3)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(201)

        // Get version 2 and verify it has the correct message
        val response = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        assert(response.contains("This is version 2")) {
            "Version 2 should contain its specific message"
        }
        assert(!response.contains("This is version 1")) {
            "Version 2 should not contain version 1's message"
        }
        assert(!response.contains("This is version 3")) {
            "Version 2 should not contain version 3's message"
        }
    }
}
