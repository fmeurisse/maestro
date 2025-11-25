package io.maestro.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Contract tests for workflow revision update endpoint.
 *
 * Tests cover:
 * - Updating inactive revisions (PUT /{namespace}/{id}/{version})
 * - Rejecting updates to active revisions (409 Conflict)
 * - Validating namespace/id/version match between URL and YAML
 * - Error cases (404 Not Found for non-existent revisions)
 * - Optimistic locking using updatedAt field (T099)
 * - Concurrent update conflict detection (409 Conflict)
 * - Sequential updates with proper timestamp tracking
 */
@QuarkusTest
class WorkflowUpdateAPIContractTest {

    @Inject
    lateinit var jdbi: Jdbi

    @BeforeEach
    fun cleanupDatabase() {
        // Truncate all workflow revisions before each test to ensure test isolation
        // TRUNCATE is faster than DELETE and resets the table completely
        jdbi.useHandle<Exception> { handle ->
            handle.execute("TRUNCATE TABLE workflow_revisions RESTART IDENTITY CASCADE")
        }
    }
    
    @AfterEach
    fun cleanupAfterTest() {
        // Also cleanup after each test to ensure no data leaks between tests
        jdbi.useHandle<Exception> { handle ->
            handle.execute("TRUNCATE TABLE workflow_revisions RESTART IDENTITY CASCADE")
        }
    }

    companion object {
        private const val WORKFLOW_ENDPOINT = "/api/workflows"

        private fun createWorkflowYaml(
            namespace: String,
            id: String,
            version: Int? = null,
            message: String = "Test message",
            updatedAt: String? = null
        ): String {
            val versionLine = if (version != null) "version: $version" else ""
            val updatedAtLine = if (updatedAt != null) "updatedAt: $updatedAt" else ""

            val lines = mutableListOf<String>()
            lines.add("namespace: $namespace")
            lines.add("id: $id")
            if (versionLine.isNotEmpty()) lines.add(versionLine)
            if (updatedAtLine.isNotEmpty()) lines.add(updatedAtLine)
            lines.add("name: Test Workflow")
            lines.add("description: Test description")
            lines.add("steps:")
            lines.add("  - type: LogTask")
            lines.add("    message: \"$message\"")

            return lines.joinToString("\n")
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

    // ===== PUT /{namespace}/{id}/{version} Tests =====

    @Test
    fun `should update an inactive revision successfully`() {
        val namespace = "test-ns-update"
        val id = "workflow-update"
        val initialYaml = createWorkflowYaml(namespace, id, message = "Original message")

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

        // Get existing updatedAt for optimistic locking
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Update the inactive revision with new content (including updatedAt for optimistic locking)
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            updatedAt: $existingUpdatedAt
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
        val initialYaml = createWorkflowYaml(namespace, id, message = "Original")

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

        // Activate it (with optimistic locking header)
        val updatedAtBeforeActivation = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeActivation)
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

        // Get existing updatedAt for optimistic locking
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Try to update with DIFFERENT namespace in YAML
        val updatedYaml = """
            namespace: different-namespace
            id: $id
            version: 1
            updatedAt: $existingUpdatedAt
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

        // Get existing updatedAt for optimistic locking
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Try to update with DIFFERENT id in YAML
        val updatedYaml = """
            namespace: $namespace
            id: different-id
            version: 1
            updatedAt: $existingUpdatedAt
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

        // Get existing updatedAt for optimistic locking
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Try to update with DIFFERENT version in YAML
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 99
            updatedAt: $existingUpdatedAt
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
        val initialYaml = createWorkflowYaml(namespace, id, message = "Original")

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

        val updatedAtBeforeActivation = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeActivation)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/activate")
        .then()
            .statusCode(200)

        // Deactivate it (this will update the updatedAt timestamp)
        val updatedAtBeforeDeactivation = getExistingUpdatedAt(namespace, id, 1)
        RestAssured.given()
            .header("X-Current-Updated-At", updatedAtBeforeDeactivation)
        .`when`()
            .post("$WORKFLOW_ENDPOINT/$namespace/$id/1/deactivate")
        .then()
            .statusCode(200)

        // Get existing updatedAt for optimistic locking (after deactivation)
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Now update the deactivated revision (should work now)
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            updatedAt: $existingUpdatedAt
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
        val initialYaml = createWorkflowYaml(namespace, id, message = "Original")

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
            .extract()
            .body()
            .asString()

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100)

        // Get existing updatedAt for optimistic locking
        val existingUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Update the revision
        val updatedYaml = """
            namespace: $namespace
            id: $id
            version: 1
            updatedAt: $existingUpdatedAt
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

    // ===== Optimistic Locking Tests =====

    @Test
    fun `should detect concurrent update conflict using updatedAt field`() {
        val namespace = "concurrent-ns"
        val id = "workflow-concurrent"

        // Step 1: Create initial workflow revision (version 1)
        val initialYaml = createWorkflowYaml(namespace, id, message = "Initial")
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

        // Step 2: User A reads the revision and extracts updatedAt
        val userAUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Step 3: User B reads the revision (gets same updatedAt)
        val userBUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Verify both users read the same revision state
        assert(userAUpdatedAt == userBUpdatedAt) {
            "Both users should have the same updatedAt timestamp initially"
        }

        // Step 4: User A updates the revision first (should succeed)
        // Include the updatedAt from their read in the YAML
        val userAUpdateYaml = createWorkflowYaml(
            namespace, id, version = 1,
            message = "User A update",
            updatedAt = userAUpdatedAt  // Include updatedAt in YAML body
        )
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(userAUpdateYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)  // Success - no conflict

        // Step 5: User B tries to update using stale timestamp in YAML (should fail with 409 Conflict)
        val userBUpdateYaml = createWorkflowYaml(
            namespace, id, version = 1,
            message = "User B update",
            updatedAt = userBUpdatedAt  // Stale updatedAt (same as User A had initially)
        )
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(userBUpdateYaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(409)  // Conflict - optimistic lock failure
            .contentType(CoreMatchers.containsString("application/problem+json"))
            .body("type", CoreMatchers.equalTo("https://maestro.io/problems/optimistic-lock-conflict"))
            .body("title", CoreMatchers.equalTo("Optimistic Lock Conflict"))
            .body("status", CoreMatchers.equalTo(409))
            .body("detail", CoreMatchers.containsString("has been modified by another user"))
            .body("detail", CoreMatchers.containsString("Expected updatedAt"))
            .body("detail", CoreMatchers.containsString("Actual updatedAt"))
    }

    @Test
    fun `should allow sequential updates with correct timestamps`() {
        val namespace = "sequential-ns"
        val id = "workflow-sequential"

        // Create initial workflow
        val initialYaml = createWorkflowYaml(namespace, id, message = "V1")
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

        // Update 1: Get current updatedAt and update
        var currentUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        val update1Yaml = createWorkflowYaml(
            namespace, id, version = 1,
            message = "V2",
            updatedAt = currentUpdatedAt  // Include updatedAt in YAML body
        )
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(update1Yaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)

        // Small delay to ensure first update is fully persisted
        Thread.sleep(50)

        // Update 2: Get fresh updatedAt and update again
        currentUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        val update2Yaml = createWorkflowYaml(
            namespace, id, version = 1,
            message = "V3",
            updatedAt = currentUpdatedAt  // Include fresh updatedAt in YAML body
        )
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(update2Yaml)
        .`when`()
            .put("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)  // Both sequential updates should succeed

        // Verify final state
        val finalYaml = RestAssured.given()
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        // Verify the message was updated to V3
        assert(finalYaml.contains("message: \"V3\"")) { "Final message should be V3" }
    }

    @Test
    fun `should update updatedAt timestamp after successful update`() {
        val namespace = "timestamp-ns"
        val id = "workflow-timestamp"

        // Create workflow
        val initialYaml = createWorkflowYaml(namespace, id, message = "Initial")
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

        // Get initial timestamps (API only returns YAML, so parse YAML string)
        val initialStateYaml = RestAssured.given()
            .accept("application/yaml")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        val createdAtRegex = Regex("""createdAt:\s*([^\s\n]+)""")
        val updatedAtRegex = Regex("""updatedAt:\s*([^\s\n]+)""")
        val initialCreatedAt = Instant.parse(createdAtRegex.find(initialStateYaml)?.groupValues?.get(1) ?: throw AssertionError("Could not find createdAt"))
        val initialUpdatedAt = Instant.parse(updatedAtRegex.find(initialStateYaml)?.groupValues?.get(1) ?: throw AssertionError("Could not find updatedAt"))

        // Wait a moment to ensure timestamp difference
        Thread.sleep(100)

        // Get existing updatedAt for optimistic locking
        val currentUpdatedAt = getExistingUpdatedAt(namespace, id, 1)

        // Update the workflow with updatedAt for optimistic locking
        val updatedYaml = createWorkflowYaml(
            namespace, id, version = 1,
            message = "Updated",
            updatedAt = currentUpdatedAt
        )
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

        // Get updated timestamps (API only returns YAML, so parse YAML string)
        val updatedStateYaml = RestAssured.given()
            .accept("application/yaml")
        .`when`()
            .get("$WORKFLOW_ENDPOINT/$namespace/$id/1")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        val finalCreatedAt = Instant.parse(createdAtRegex.find(updatedStateYaml)?.groupValues?.get(1) ?: throw AssertionError("Could not find createdAt"))
        val finalUpdatedAt = Instant.parse(updatedAtRegex.find(updatedStateYaml)?.groupValues?.get(1) ?: throw AssertionError("Could not find updatedAt"))

        // Verify: createdAt unchanged, updatedAt changed
        assert(initialCreatedAt == finalCreatedAt) {
            "createdAt should remain unchanged"
        }
        assert(finalUpdatedAt.isAfter(initialUpdatedAt)) {
            "updatedAt should be updated to a later timestamp"
        }
    }
}
