package io.maestro.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.Test

/**
 * Contract tests for the WorkflowResource create workflow endpoint.
 *
 * These tests verify the API contract (request/response format, status codes, headers)
 * without testing full business logic. They ensure the API adheres to the specified contract.
 *
 * Tests cover:
 * - Successful workflow creation (201 Created)
 * - Response format (YAML with correct structure)
 * - Location header presence and format
 * - Error cases (400 Bad Request for invalid YAML, 409 Conflict for duplicates)
 * - Content-Type headers (request and response)
 */
@QuarkusTest
class CreateWorkflowAPIContractTest : AbstractAPIContractTest() {


    companion object {
        private const val WORKFLOW_ENDPOINT = "/api/workflows"

        private val INVALID_YAML = """
            namespace: test-ns
            id: test-workflow
            name: Test Workflow
            invalid: [unclosed bracket
        """.trimIndent()

        private val MISSING_REQUIRED_FIELDS_YAML = """
            namespace: test-ns
            name: Test Workflow
        """.trimIndent()
    }

    @Test
    fun `should create workflow successfully and return 201 Created`() {
        val uniqueWorkflowYaml = """
            namespace: test-ns-success
            id: test-workflow-success
            name: Test Workflow
            description: A test workflow for success testing
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Test message"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(uniqueWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
            .contentType("application/yaml")
            .header("Location", CoreMatchers.notNullValue())
            .body(CoreMatchers.containsString("namespace:"))
            .body(CoreMatchers.containsString("id:"))
            .body(CoreMatchers.containsString("version:"))
    }

    @Test
    fun `should return Location header with correct format`() {
        val uniqueWorkflowYaml = """
            namespace: test-ns-location
            id: test-workflow-location
            name: Test Workflow
            description: A test workflow for location header testing
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Test message"
        """.trimIndent()

        val location = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body(uniqueWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
            .extract()
            .header("Location")

        // Verify Location header format: http://host:port/api/workflows/{namespace}/{id}/{version}
        assert(location != null) { "Location header should not be null" }
        assert(location!!.matches(Regex("http://[^/]+/api/workflows/[^/]+/[^/]+/\\d+"))) {
            "Location header '$location' does not match expected format http://host:port/api/workflows/{namespace}/{id}/{version}"
        }
    }

    @Test
    fun `should return YAML response with required fields`() {
        val uniqueWorkflowYaml = """
            namespace: test-ns-fields
            id: test-workflow-fields
            name: Test Workflow
            description: A test workflow for fields testing
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Test message"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body(uniqueWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
            .contentType(CoreMatchers.containsString("application/yaml"))
            .body(CoreMatchers.containsString("namespace: test-ns-fields"))
            .body(CoreMatchers.containsString("id: test-workflow-fields"))
            .body(CoreMatchers.containsString("version:"))
    }

    @Test
    fun `should return 400 Bad Request for invalid YAML syntax`() {
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body(INVALID_YAML)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.notNullValue())
            .body("title", CoreMatchers.notNullValue())
            .body("status", CoreMatchers.equalTo(400))
            .body("detail", CoreMatchers.notNullValue())
    }

    @Test
    fun `should return 400 Bad Request for missing required fields`() {
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body(MISSING_REQUIRED_FIELDS_YAML)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.notNullValue())
            .body("status", CoreMatchers.equalTo(400))
    }

    @Test
    fun `should return 409 Conflict when creating duplicate workflow`() {
        val duplicateWorkflowYaml = """
            namespace: test-ns-duplicate
            id: test-workflow-duplicate
            name: Test Workflow
            description: A test workflow for duplicate testing
            steps:
              - type: LogTask
                message: "Test message"
        """.trimIndent()

        // Create first workflow
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT))
            )
            .contentType("application/yaml")
            .body(duplicateWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)

        // Attempt to create duplicate (same namespace + id)
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(duplicateWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.notNullValue())
            .body("title", CoreMatchers.notNullValue())
            .body("status", CoreMatchers.equalTo(409))
            .body("detail", CoreMatchers.notNullValue())
    }

    @Test
    fun `should return 415 Unsupported Media Type for wrong content type`() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""{"namespace": "test-ns", "id": "test-workflow"}""")
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(415)
    }

    @Test
    fun `should accept application-yaml content type`() {
        val uniqueWorkflowYaml = """
            namespace: test-ns-yaml
            id: test-workflow-yaml
            name: Test Workflow
            description: A test workflow for yaml content type testing
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Test message"
        """.trimIndent()

        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
            ))
            .contentType("application/yaml")
            .body(uniqueWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
    }

    @Test
    fun `should return version 1 for first workflow creation`() {
        val uniqueWorkflowYaml = """
            namespace: test-ns-version
            id: test-workflow-version
            name: Test Workflow
            description: A test workflow for version testing
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Test message"
        """.trimIndent()

        val responseBody = RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body(uniqueWorkflowYaml)
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(201)
            .extract()
            .body()
            .asString()

        // Verify version is 1
        assert(responseBody.contains("version: 1") || responseBody.contains("version:1"))
    }

    @Test
    fun `should handle empty request body with 400 Bad Request`() {
        RestAssured.given()
            .config(RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)
            ))
            .contentType("application/x-yaml")
            .body("")
        .`when`()
            .post(WORKFLOW_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
    }
}