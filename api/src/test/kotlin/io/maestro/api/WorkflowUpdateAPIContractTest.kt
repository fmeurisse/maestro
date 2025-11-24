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
 * Contract tests for workflow revision update endpoint.
 *
 * Tests cover:
 * - Updating inactive revisions (PUT /{namespace}/{id}/{version})
 * - Rejecting updates to active revisions (409 Conflict)
 * - Validating namespace/id/version match between URL and YAML
 * - Error cases (404 Not Found for non-existent revisions)
 */
@QuarkusTest
class WorkflowUpdateAPIContractTest {

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
    }

    // ===== PUT /{namespace}/{id}/{version} Tests =====

    @Test
    fun `should update an inactive revision successfully`() {
        val namespace = "test-ns-update"
        val id = "workflow-update"
        val initialYaml = createWorkflowYaml(namespace, id, "Original message")

        // Create workflow (starts as inactive)
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Update the inactive revision with new content
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            name: Updated Workflow
            description: Updated description
            steps:
              - type: LogTask
                message: "Updated message"
        """.trimIndent()

        val updateResponse = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .extract()
            .body()
            .asString()

        // Verify the response contains the revision ID
        assert(updateResponse.contains(namespace)) { "Response should contain namespace" }
        assert(updateResponse.contains(id)) { "Response should contain id" }
        assert(updateResponse.contains("version: 1") || updateResponse.contains("version:1")) {
            "Response should contain version 1"
        }

        // Verify the updated content by retrieving the revision
        val getResponse = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Check that the new content is present
        assert(getResponse.contains("Updated Workflow")) { "Should have updated name" }
        assert(getResponse.contains("Updated description")) { "Should have updated description" }
        assert(getResponse.contains("Updated message")) { "Should have updated message" }
    }

    @Test
    fun `should return 409 when updating an active revision`() {
        val namespace = "test-ns-update-active"
        val id = "workflow-update-active"
        val initialYaml = createWorkflowYaml(namespace, id, "Original")

        // Create and activate workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Activate it
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Try to update the ACTIVE revision (should fail)
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            name: Trying to update
            description: This should fail
            steps:
              - type: LogTask
                message: "Should not work"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("active-revision-conflict"))
            .body("status", CoreMatchers.equalTo(409))
    }

    @Test
    fun `should return 404 when updating non-existent revision`() {
        val updatedYaml = """
            namespace: non-existent-ns
            id: non-existent-id
            version: 1
            name: Does not exist
            description: Test
            steps:
              - type: LogTask
                message: "Test"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-revision-not-found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should validate namespace match between URL and YAML`() {
        val namespace = "test-ns-validate"
        val id = "workflow-validate"
        val initialYaml = createWorkflowYaml(namespace, id)

        // Create workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Try to update with DIFFERENT namespace in YAML
        val updatedYaml = """
            namespace: different-namespace
            id: $id
            version: 1
            name: Test
            description: Test
            steps:
              - type: LogTask
                message: "Test"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("invalid-workflow-revision"))
            .body("status", CoreMatchers.equalTo(400))
    }

    @Test
    fun `should validate workflow ID match between URL and YAML`() {
        val namespace = "test-ns-validate-id"
        val id = "workflow-validate-id"
        val initialYaml = createWorkflowYaml(namespace, id)

        // Create workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Try to update with DIFFERENT id in YAML
        val updatedYaml = """
            namespace: $namespace
            id: different-id
            version: 1
            name: Test
            description: Test
            steps:
              - type: LogTask
                message: "Test"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("invalid-workflow-revision"))
            .body("status", CoreMatchers.equalTo(400))
    }

    @Test
    fun `should validate version match between URL and YAML`() {
        val namespace = "test-ns-validate-version"
        val id = "workflow-validate-version"
        val initialYaml = createWorkflowYaml(namespace, id)

        // Create workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Try to update with DIFFERENT version in YAML
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 99
            name: Test
            description: Test
            steps:
              - type: LogTask
                message: "Test"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("invalid-workflow-revision"))
            .body("status", CoreMatchers.equalTo(400))
    }

    @Test
    fun `should allow update after deactivation`() {
        val namespace = "test-ns-update-after-deactivate"
        val id = "workflow-update-after-deactivate"
        val initialYaml = createWorkflowYaml(namespace, id, "Original")

        // Create and activate workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Deactivate it
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/deactivate")
        .then()
            .statusCode(200)

        // Now update should work
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            name: Updated after deactivation
            description: This should work now
            steps:
              - type: LogTask
                message: "Updated successfully"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)

        // Verify the update
        val getResponse = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        assert(getResponse.contains("Updated after deactivation")) { "Should have updated name" }
        assert(getResponse.contains("Updated successfully")) { "Should have updated message" }
    }

    @Test
    fun `should preserve immutable fields when updating`() {
        val namespace = "test-ns-preserve"
        val id = "workflow-preserve"
        val initialYaml = createWorkflowYaml(namespace, id, "Original")

        // Create workflow
        val createResponse = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(initialYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
            .extract()
            .body()
            .asString()

        // Get the original content to compare timestamps
        val originalContent = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100)

        // Update the revision
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            name: Updated
            description: Updated
            steps:
              - type: LogTask
                message: "Updated"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(updatedYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)

        // Get the updated content
        val updatedContent = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Verify version is still 1
        assert(updatedContent.contains("version: 1") || updatedContent.contains("version:1")) {
            "Version should remain 1"
        }

        // Verify namespace and id are preserved
        assert(updatedContent.contains(namespace)) { "Namespace should be preserved" }
        assert(updatedContent.contains(id)) { "ID should be preserved" }

        // Verify content was actually updated
        assert(updatedContent.contains("Updated")) { "Content should be updated" }
    }
}
