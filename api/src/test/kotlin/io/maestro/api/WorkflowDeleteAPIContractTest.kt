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
 * Contract tests for workflow and revision delete endpoints.
 *
 * Tests cover:
 * - Deleting inactive revisions (DELETE /{namespace}/{id}/{version})
 * - Deleting entire workflows (DELETE /{namespace}/{id})
 * - Rejecting deletion of active revisions (409 Conflict)
 * - Error cases (404 Not Found for non-existent revisions)
 */
@QuarkusTest
class WorkflowDeleteAPIContractTest {

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

    // ===== DELETE /{namespace}/{id}/{version} Tests =====

    @Test
    fun `should delete an inactive revision successfully`() {
        val namespace = "test-ns-delete"
        val id = "workflow-delete"
        val yaml = createWorkflowYaml(namespace, id)

        // Create workflow (starts as inactive)
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

        // Delete the inactive revision
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(204)

        // Verify it's deleted - GET should return 404
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should return 404 when deleting non-existent revision`() {
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id/1")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-revision-not-found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should return 409 when deleting an active revision`() {
        val namespace = "test-ns-delete-active"
        val id = "workflow-delete-active"
        val yaml = createWorkflowYaml(namespace, id)

        // Create and activate workflow
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

        // Activate it
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Try to delete the ACTIVE revision (should fail)
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("active-revision-conflict"))
            .body("status", CoreMatchers.equalTo(409))

        // Verify it still exists
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
    }

    @Test
    fun `should allow deletion after deactivation`() {
        val namespace = "test-ns-delete-after-deactivate"
        val id = "workflow-delete-after-deactivate"
        val yaml = createWorkflowYaml(namespace, id)

        // Create and activate workflow
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

        // Now delete should work
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(204)

        // Verify it's deleted
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should delete one revision while leaving others intact`() {
        val namespace = "test-ns-delete-one"
        val id = "workflow-delete-one"

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

        // Delete version 2
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(204)

        // Verify version 2 is gone
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(404)

        // Verify versions 1 and 3 still exist
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)

        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/3")
        .then()
            .statusCode(200)
    }

    // ===== DELETE /{namespace}/{id} Tests =====

    @Test
    fun `should delete all revisions of a workflow`() {
        val namespace = "test-ns-delete-all"
        val id = "workflow-delete-all"

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

        // Delete entire workflow
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(204)

        // Verify all revisions are gone
        for (version in 1..3) {
            RestAssured.given()
            .`when`()
                .get("$WORKFLOW_ENDPOINT/$namespace/$id/$version")
            .then()
                .statusCode(404)
        }
    }

    @Test
    fun `should return 409 when deleting workflow with active revisions`() {
        val namespace = "test-ns-delete-with-active"
        val id = "workflow-delete-with-active"

        // Create 2 revisions and activate the first one
        for (version in 1..2) {
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

        // Activate version 1
        RestAssured.given()
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Try to delete entire workflow (should fail because of active revision)
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("active-revision-conflict"))
            .body("status", CoreMatchers.equalTo(409))

        // Verify both revisions still exist
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)

        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(200)
    }

    @Test
    fun `should delete workflow after deactivating all revisions`() {
        val namespace = "test-ns-delete-after-deactivate-all"
        val id = "workflow-delete-after-deactivate-all"

        // Create 2 revisions and activate both
        for (version in 1..2) {
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

            // Activate each revision
            RestAssured.given()
            .`when`()
                .post("$WORKFLOW_ENDPOINT/$namespace/$id/$version/activate")
            .then()
                .statusCode(200)
        }

        // Deactivate all revisions
        for (version in 1..2) {
            RestAssured.given()
            .`when`()
                .post("$WORKFLOW_ENDPOINT/$namespace/$id/$version/deactivate")
            .then()
                .statusCode(200)
        }

        // Now delete entire workflow should work
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(204)

        // Verify both revisions are gone
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(404)

        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should return 204 when deleting non-existent workflow`() {
        // DELETE on non-existent workflow returns 204 (idempotent)
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/non-existent-ns/non-existent-id")
        .then()
            .statusCode(204)
    }

    @Test
    fun `should delete workflow with single revision`() {
        val namespace = "test-ns-delete-single"
        val id = "workflow-delete-single"
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

        // Delete entire workflow
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(204)

        // Verify it's deleted
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(404)
    }

    @Test
    fun `should handle deletion in correct sequence - revision then workflow`() {
        val namespace = "test-ns-sequence"
        val id = "workflow-sequence"

        // Create 2 revisions
        for (version in 1..2) {
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

        // Delete one revision first
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(204)

        // Verify only version 2 remains
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(200)

        // Delete entire workflow (should delete remaining revision)
        RestAssured.given()
        .`when`()
            .delete("$WORKFLOW_ENDPOINT/$namespace/$id")
        .then()
            .statusCode(204)

        // Verify everything is gone
        RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/2")
        .then()
            .statusCode(404)
    }
}
