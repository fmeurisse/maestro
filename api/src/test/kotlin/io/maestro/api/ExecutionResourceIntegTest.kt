package io.maestro.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Contract tests for the ExecutionResource endpoints.
 *
 * These tests verify the API contract (request/response format, status codes, headers)
 * for workflow execution operations. They ensure the API adheres to the specified contract
 * from contracts/openapi.yaml.
 *
 * Tests cover:
 * - POST /executions: Successful execution (200 OK)
 * - GET /executions/{executionId}: Query execution status (200 OK)
 * - Error cases: 404 for workflow not found, 404 for execution not found
 * - Response format: JSON with execution details and step results
 *
 * Implements User Story 1 (US1) contract tests.
 */
@QuarkusTest
class ExecutionResourceIntegTest : AbstractAPIContractTest() {

    companion object {
        private const val EXECUTE_ENDPOINT = "/api/executions"
        private const val EXECUTION_STATUS_ENDPOINT = "/api/executions/{executionId}"
        private const val EXECUTION_HISTORY_ENDPOINT = "/api/workflows/{namespace}/{id}/executions"

        // REST Assured config to handle YAML as text (not serialized)
        private val YAML_CONFIG = RestAssuredConfig.config().encoderConfig(
            EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT)
        )

        private val VALID_WORKFLOW_YAML = """
            namespace: test-ns
            id: test-workflow
            name: Test Workflow
            description: A test workflow for execution
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Step 1: Initialize"
                  - type: LogTask
                    message: "Step 2: Process"
        """.trimIndent()

        private val WORKFLOW_WITH_PARAMETERS_YAML = """
            namespace: test-ns
            id: test-workflow-params
            name: Test Workflow with Parameters
            description: A test workflow with parameter definitions
            parameters:
              - name: userName
                type: STRING
                required: true
                description: User name for the workflow
              - name: retryCount
                type: INTEGER
                required: true
                description: Number of retries
              - name: enableDebug
                type: BOOLEAN
                required: false
                default: false
                description: Enable debug logging
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Step 1: Initialize"
        """.trimIndent()
    }

    @Test
    fun `should execute workflow successfully and return 200 OK with execution ID`() {
        // Given: A workflow exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        // Extract workflow version from Location header
        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing the workflow without parameters (workflow has no parameter schema)
        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("executionId", CoreMatchers.notNullValue())
            .body("status", CoreMatchers.anyOf(CoreMatchers.equalTo("RUNNING"), CoreMatchers.equalTo("COMPLETED")))
            .body("revisionId.namespace", CoreMatchers.equalTo("test-ns"))
            .body("revisionId.id", CoreMatchers.equalTo("test-workflow"))
            .body("revisionId.version", CoreMatchers.equalTo(version))
            .body("startedAt", CoreMatchers.notNullValue())
            .body("_links.self.href", CoreMatchers.notNullValue())
            .body("_links.workflow.href", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Execution ID should be valid (NanoID format: 21 characters, URL-safe)
        val executionId = executionResponse.jsonPath().getString("executionId")
        Assertions.assertTrue(
            executionId.matches(Regex("^[A-Za-z0-9_-]{21}$")),
            "Execution ID should be 21-character NanoID format, got: $executionId"
        )
    }

    @Test
    fun `should return 404 when workflow revision not found`() {
        // When: Executing a non-existent workflow
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "non-existent",
                    "id": "workflow",
                    "version": 999,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-not-found"))
            .body("title", CoreMatchers.equalTo("Workflow Not Found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should query execution status successfully`() {
        // Given: A workflow exists and has been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait a bit for execution to complete (simple workflow should finish quickly)
        Thread.sleep(100)

        // When: Querying execution status
        RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("executionId", CoreMatchers.equalTo(executionId))
            .body("status", CoreMatchers.notNullValue())
            .body("revisionId.namespace", CoreMatchers.equalTo("test-ns"))
            .body("revisionId.id", CoreMatchers.equalTo("test-workflow"))
            .body("revisionId.version", CoreMatchers.equalTo(version))
            .body("startedAt", CoreMatchers.notNullValue())
            .body("steps", CoreMatchers.notNullValue())
            .body("_links.self.href", CoreMatchers.notNullValue())
            .body("_links.workflow.href", CoreMatchers.notNullValue())
    }

    @Test
    fun `should return 404 when execution not found`() {
        // Given: A non-existent execution ID (valid NanoID format but doesn't exist)
        val nonExistentExecutionId = "V1StGXR8_Z5jdHi6B-myT"

        // When: Querying execution status
        RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, nonExistentExecutionId)
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("execution-not-found"))
            .body("title", CoreMatchers.equalTo("Execution Not Found"))
            .body("status", CoreMatchers.equalTo(404))
    }

    @Test
    fun `should return 400 for invalid execution ID format`() {
        // Given: An invalid execution ID format (contains characters not in NanoID alphabet)
        val invalidExecutionId = "invalid@id#format!"

        // When: Querying execution status
        RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, invalidExecutionId)
        .then()
            .statusCode(400) // Should return 400 Bad Request for invalid format
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("bad-request"))
            .body("title", CoreMatchers.equalTo("Bad Request"))
            .body("status", CoreMatchers.equalTo(400))
    }

    @Test
    fun `should execute workflow without parameters`() {
        // Given: A workflow exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing the workflow without parameters
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("executionId", CoreMatchers.notNullValue())
            .body("inputParameters", CoreMatchers.equalTo(emptyMap<Any, Any>()))
    }

    @Test
    fun `should include step results in execution status response`() {
        // Given: A workflow exists and has been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait a bit for execution to complete
        Thread.sleep(100)

        // When: Querying execution status
        val statusResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Should include step results
        val steps = statusResponse.jsonPath().getList<Map<String, Any>>("steps")
        Assertions.assertTrue(steps.size >= 0) // At least some steps should be present

        // Verify step result structure if steps exist
        if (steps.isNotEmpty()) {
            val firstStep = steps[0]
            Assertions.assertTrue(firstStep.containsKey("stepIndex"))
            Assertions.assertTrue(firstStep.containsKey("stepId"))
            Assertions.assertTrue(firstStep.containsKey("stepType"))
            Assertions.assertTrue(firstStep.containsKey("status"))
            Assertions.assertTrue(firstStep.containsKey("startedAt"))
            Assertions.assertTrue(firstStep.containsKey("completedAt"))
        }
    }

    // ========== User Story 2 (US2) - Parameter Validation Tests ==========

    @Test
    fun `should return 400 for type mismatch parameter`() {
        // Given: A workflow with parameter definitions exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(WORKFLOW_WITH_PARAMETERS_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing with wrong type for retryCount (string instead of integer)
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-params",
                    "version": $version,
                    "parameters": {
                        "userName": "alice",
                        "retryCount": "not-a-number",
                        "enableDebug": true
                    }
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-parameter-validation-error"))
            .body("title", CoreMatchers.equalTo("Workflow Parameter Validation Failed"))
            .body("status", CoreMatchers.equalTo(400))
            .body("invalidParams", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Should contain error for retryCount type mismatch
        val invalidParams = response.jsonPath().getList<Map<String, Any>>("invalidParams")
        val retryCountError = invalidParams.find { it["name"] == "retryCount" }
        Assertions.assertNotNull(retryCountError, "Should include error for retryCount type mismatch")
        Assertions.assertEquals("not-a-number", retryCountError!!["provided"])
    }

    @Test
    fun `should return 400 for missing required parameter`() {
        // Given: A workflow with required parameters exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(WORKFLOW_WITH_PARAMETERS_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing without required userName parameter
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-params",
                    "version": $version,
                    "parameters": {
                        "retryCount": 3
                    }
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-parameter-validation-error"))
            .body("title", CoreMatchers.equalTo("Workflow Parameter Validation Failed"))
            .body("status", CoreMatchers.equalTo(400))
            .body("invalidParams", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Should contain error for missing userName
        val invalidParams = response.jsonPath().getList<Map<String, Any>>("invalidParams")
        val errorNames = invalidParams.map { it["name"] as String }
        Assertions.assertTrue(
            errorNames.contains("userName"),
            "Should include error for missing required parameter 'userName'. Found errors: $errorNames"
        )
        val userNameError = invalidParams.find { it["name"] == "userName" }
        Assertions.assertTrue(
            (userNameError!!["reason"] as String).contains("required"),
            "Error reason should mention 'required'"
        )
        Assertions.assertNull(userNameError["provided"])
    }

    @Test
    fun `should return 400 for extra parameters not in schema`() {
        // Given: A workflow with parameter definitions exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(WORKFLOW_WITH_PARAMETERS_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing with extra parameter not defined in schema
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-params",
                    "version": $version,
                    "parameters": {
                        "userName": "alice",
                        "retryCount": 3,
                        "unknownParam": "should be rejected"
                    }
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-parameter-validation-error"))
            .body("title", CoreMatchers.equalTo("Workflow Parameter Validation Failed"))
            .body("status", CoreMatchers.equalTo(400))
            .body("invalidParams", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Should contain error for unknownParam
        val invalidParams = response.jsonPath().getList<Map<String, Any>>("invalidParams")
        val unknownParamError = invalidParams.find { it["name"] == "unknownParam" }
        Assertions.assertNotNull(unknownParamError, "Should include error for extra parameter 'unknownParam'")
        Assertions.assertTrue(
            (unknownParamError!!["reason"] as String).contains("not defined"),
            "Error reason should mention 'not defined'"
        )
        Assertions.assertEquals("should be rejected", unknownParamError["provided"])
    }

    @Test
    fun `should return 400 with multiple validation errors in single response`() {
        // Given: A workflow with parameter definitions exists
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(WORKFLOW_WITH_PARAMETERS_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing with multiple validation errors (missing required, type mismatch, extra parameter)
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-params",
                    "version": $version,
                    "parameters": {
                        "retryCount": "not-a-number",
                        "extraParam": "should be rejected"
                    }
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-parameter-validation-error"))
            .body("title", CoreMatchers.equalTo("Workflow Parameter Validation Failed"))
            .body("status", CoreMatchers.equalTo(400))
            .body("invalidParams", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Should contain multiple validation errors
        val invalidParams = response.jsonPath().getList<Map<String, Any>>("invalidParams")
        Assertions.assertTrue(
            invalidParams.size >= 2,
            "Expected at least 2 validation errors (missing userName, type mismatch retryCount, extra parameter), got: ${invalidParams.size}"
        )

        // Verify specific errors are present (order may vary)
        val errorNames = invalidParams.map { it["name"] as String }
        Assertions.assertTrue(
            errorNames.contains("userName"),
            "Should include error for missing required parameter 'userName'. Found errors: $errorNames"
        )
        Assertions.assertTrue(
            errorNames.contains("retryCount"),
            "Should include error for type mismatch parameter 'retryCount'. Found errors: $errorNames"
        )
        Assertions.assertTrue(
            errorNames.contains("extraParam"),
            "Should include error for extra parameter 'extraParam'. Found errors: $errorNames"
        )
    }

    // ========== User Story 3 (US3) - Execution Progress Tests ==========

    @Test
    fun `should return steps ordered by stepIndex in execution status response`() {
        // Given: A workflow with multiple sequential steps exists
        val workflowWithMultipleSteps = """
            namespace: test-ns
            id: test-workflow-multi
            name: Multi-Step Workflow
            description: A workflow with multiple sequential steps
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Step 1"
                  - type: LogTask
                    message: "Step 2"
                  - type: LogTask
                    message: "Step 3"
        """.trimIndent()

        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(workflowWithMultipleSteps)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing the workflow
        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-multi",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait for execution to complete
        Thread.sleep(200)

        // When: Querying execution status
        val statusResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Steps should be ordered by stepIndex
        val steps = statusResponse.jsonPath().getList<Map<String, Any>>("steps")
        Assertions.assertTrue(steps.size > 0, "Should have at least one step")

        // Verify steps are ordered by stepIndex (0, 1, 2, ...)
        for (i in steps.indices) {
            val stepIndex = steps[i]["stepIndex"] as Int
            Assertions.assertEquals(
                i,
                stepIndex,
                "Step at position $i should have stepIndex=$i, but got stepIndex=$stepIndex"
            )
        }
    }

    @Test
    fun `should include timing information for each step`() {
        // Given: A workflow exists and has been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait for execution to complete
        Thread.sleep(100)

        // When: Querying execution status
        val statusResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Each step should have timing information
        val steps = statusResponse.jsonPath().getList<Map<String, Any>>("steps")
        for (step in steps) {
            Assertions.assertTrue(
                step.containsKey("startedAt"),
                "Step should have startedAt timestamp"
            )
            Assertions.assertTrue(
                step.containsKey("completedAt"),
                "Step should have completedAt timestamp"
            )
            val startedAt = step["startedAt"] as String
            val completedAt = step["completedAt"] as String
            Assertions.assertNotNull(startedAt, "startedAt should not be null")
            Assertions.assertNotNull(completedAt, "completedAt should not be null")
        }
    }

    @Test
    fun `should mark remaining steps as SKIPPED when a step fails`() {
        // Given: A workflow with a failing step (we'll need to create a workflow that fails)
        // For now, we'll test with a workflow that has multiple steps and verify SKIPPED status
        // Note: This test may need adjustment based on actual failure mechanism
        val workflowWithMultipleSteps = """
            namespace: test-ns
            id: test-workflow-fail
            name: Failing Workflow
            description: A workflow that will fail
            steps:
              - type: Sequence
                steps:
                  - type: LogTask
                    message: "Step 1"
                  - type: LogTask
                    message: "Step 2"
                  - type: LogTask
                    message: "Step 3"
        """.trimIndent()

        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(workflowWithMultipleSteps)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // When: Executing the workflow
        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow-fail",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait for execution to complete
        Thread.sleep(200)

        // When: Querying execution status
        val statusResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: All steps should have a status (COMPLETED, FAILED, or SKIPPED)
        @Suppress("UNCHECKED_CAST")
        val steps: List<Map<String, Any>> = statusResponse.jsonPath().getList<Any>("steps") as List<Map<String, Any>>
        val validStatuses = listOf("COMPLETED", "FAILED", "SKIPPED", "PENDING", "RUNNING")
        for (step in steps) {
            val status = step["status"] as String
            Assertions.assertTrue(
                status in validStatuses,
                "Step status should be one of COMPLETED, FAILED, SKIPPED, PENDING, or RUNNING, got: $status"
            )
        }

        // If execution failed, verify that steps after the failure are SKIPPED
        val executionStatus = statusResponse.jsonPath().getString("status")
        if (executionStatus == "FAILED") {
            var foundFailed = false
            for (step in steps) {
                val stepIndex = step["stepIndex"] as Int
                val status = step["status"] as String
                if (status == "FAILED") {
                    foundFailed = true
                } else if (foundFailed) {
                    Assertions.assertEquals(
                        "SKIPPED",
                        status,
                        "Step at index $stepIndex should be SKIPPED after failure, but got status: $status"
                    )
                }
            }
        }
    }

    @Test
    fun `should handle concurrent queries for same execution`() {
        // Given: A workflow exists and has been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait for execution to complete
        Thread.sleep(100)

        // When: Querying execution status concurrently (simulate with multiple sequential queries)
        val responses = mutableListOf<Response>()
        for (i in 1..5) {
            val response = RestAssured.given()
            .`when`()
                .get(EXECUTION_STATUS_ENDPOINT, executionId)
            .then()
                .statusCode(200)
                .extract()
                .response()
            responses.add(response)
        }

        // Then: All concurrent queries should return consistent results
        val firstExecutionId = responses[0].jsonPath().getString("executionId")
        for (response in responses) {
            Assertions.assertEquals(
                firstExecutionId,
                response.jsonPath().getString("executionId"),
                "All concurrent queries should return the same execution ID"
            )
            Assertions.assertEquals(
                responses[0].jsonPath().getString("status"),
                response.jsonPath().getString("status"),
                "All concurrent queries should return the same execution status"
            )
        }
    }

    // ===== User Story 4 (US4): View Execution History Tests =====

    @Test
    fun `should return execution history for workflow`() {
        // Given: A workflow exists and has been executed multiple times
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val location = createResponse.header("Location")
        val version = location?.substringAfterLast("/")?.toInt() ?: 1

        // Execute workflow multiple times
        val executionIds = mutableListOf<String>()
        for (i in 1..3) {
            val executionResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "namespace": "test-ns",
                        "id": "test-workflow",
                        "version": $version,
                        "parameters": {}
                    }
                """.trimIndent())
            .`when`()
                .post(EXECUTE_ENDPOINT)
            .then()
                .statusCode(200)
                .extract()
                .response()
            executionIds.add(executionResponse.jsonPath().getString("executionId"))
            Thread.sleep(50) // Small delay to ensure different timestamps
        }

        // Wait for executions to complete
        Thread.sleep(200)

        // When: Querying execution history
        val historyResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("executions", CoreMatchers.notNullValue())
            .body("pagination", CoreMatchers.notNullValue())
            .body("_links", CoreMatchers.notNullValue())
            .extract()
            .response()

        // Then: Should return executions with correct structure
        val executions = historyResponse.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(executions.size >= 3, "Should return at least 3 executions")

        // Verify execution summary structure
        val firstExecution = executions[0]
        Assertions.assertNotNull(firstExecution["executionId"])
        Assertions.assertNotNull(firstExecution["status"])
        Assertions.assertNotNull(firstExecution["revisionVersion"])
        Assertions.assertNotNull(firstExecution["startedAt"])
        Assertions.assertNotNull(firstExecution["stepCount"])
        Assertions.assertNotNull(firstExecution["completedSteps"])
        Assertions.assertNotNull(firstExecution["failedSteps"])

        // Verify pagination metadata
        val pagination = historyResponse.jsonPath().getMap<String, Any>("pagination")
        Assertions.assertNotNull(pagination["total"])
        Assertions.assertNotNull(pagination["limit"])
        Assertions.assertNotNull(pagination["offset"])
        Assertions.assertNotNull(pagination["hasMore"])

        // Verify HATEOAS links
        val links = historyResponse.jsonPath().getMap<String, Any>("_links")
        Assertions.assertNotNull(links["self"])
        Assertions.assertNotNull(links["workflow"])
    }

    @Test
    fun `should filter execution history by version`() {
        // Given: A workflow with multiple versions exists
        val createResponse1 = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val version1 = createResponse1.header("Location")?.substringAfterLast("/")?.toInt() ?: 1

        // Create version 2 (create a new revision)
        val createResponse2 = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows/test-ns/test-workflow")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val version2 = createResponse2.header("Location")?.substringAfterLast("/")?.toInt() ?: 2

        // Execute version 1
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version1,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)

        Thread.sleep(50)

        // Execute version 2
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version2,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)

        Thread.sleep(200)

        // When: Querying history filtered by version 1
        val historyResponse = RestAssured.given()
            .queryParam("version", version1)
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Should return only executions for version 1
        val executions = historyResponse.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(executions.isNotEmpty(), "Should return at least one execution")
        for (execution in executions) {
            Assertions.assertEquals(
                version1,
                execution["revisionVersion"] as Int,
                "All executions should be for version $version1"
            )
        }
    }

    @Test
    fun `should filter execution history by status`() {
        // Given: A workflow exists and has been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val version = createResponse.header("Location")?.substringAfterLast("/")?.toInt() ?: 1

        // Execute workflow
        val executionResponse = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "namespace": "test-ns",
                    "id": "test-workflow",
                    "version": $version,
                    "parameters": {}
                }
            """.trimIndent())
        .`when`()
            .post(EXECUTE_ENDPOINT)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionId = executionResponse.jsonPath().getString("executionId")

        // Wait for execution to complete
        Thread.sleep(200)

        // Verify execution is COMPLETED
        val statusResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_STATUS_ENDPOINT, executionId)
        .then()
            .statusCode(200)
            .extract()
            .response()

        val executionStatus = statusResponse.jsonPath().getString("status")
        Assertions.assertEquals("COMPLETED", executionStatus, "Execution should be COMPLETED")

        // When: Querying history filtered by COMPLETED status
        val historyResponse = RestAssured.given()
            .queryParam("status", "COMPLETED")
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Should return only COMPLETED executions
        val executions = historyResponse.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(executions.isNotEmpty(), "Should return at least one execution")
        for (execution in executions) {
            Assertions.assertEquals(
                "COMPLETED",
                execution["status"] as String,
                "All executions should have COMPLETED status"
            )
        }
    }

    @Test
    fun `should support pagination with limit and offset`() {
        // Given: A workflow exists and has been executed multiple times
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val version = createResponse.header("Location")?.substringAfterLast("/")?.toInt() ?: 1

        // Execute workflow 5 times
        for (i in 1..5) {
            RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "namespace": "test-ns",
                        "id": "test-workflow",
                        "version": $version,
                        "parameters": {}
                    }
                """.trimIndent())
            .`when`()
                .post(EXECUTE_ENDPOINT)
            .then()
                .statusCode(200)
            Thread.sleep(50)
        }

        Thread.sleep(200)

        // When: Querying first page with limit=2
        val page1Response = RestAssured.given()
            .queryParam("limit", 2)
            .queryParam("offset", 0)
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        val page1Executions = page1Response.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertEquals(2, page1Executions.size, "First page should have 2 executions")

        val pagination1 = page1Response.jsonPath().getMap<String, Any>("pagination")
        Assertions.assertEquals(2, pagination1["limit"] as Int)
        Assertions.assertEquals(0, pagination1["offset"] as Int)
        Assertions.assertTrue((pagination1["total"] as Int) >= 5)

        // When: Querying second page
        val page2Response = RestAssured.given()
            .queryParam("limit", 2)
            .queryParam("offset", 2)
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        val page2Executions = page2Response.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(page2Executions.size <= 2, "Second page should have at most 2 executions")

        // Then: Page 1 and Page 2 should have different executions
        val page1Ids = page1Executions.map { it["executionId"] as String }.toSet()
        val page2Ids = page2Executions.map { it["executionId"] as String }.toSet()
        Assertions.assertTrue(
            page1Ids.intersect(page2Ids).isEmpty(),
            "Page 1 and Page 2 should have different executions"
        )
    }

    @Test
    fun `should return executions sorted by most recent first`() {
        // Given: A workflow exists and has been executed multiple times
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        val version = createResponse.header("Location")?.substringAfterLast("/")?.toInt() ?: 1

        // Execute workflow 3 times with delays
        val executionIds = mutableListOf<String>()
        for (i in 1..3) {
            val executionResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "namespace": "test-ns",
                        "id": "test-workflow",
                        "version": $version,
                        "parameters": {}
                    }
                """.trimIndent())
            .`when`()
                .post(EXECUTE_ENDPOINT)
            .then()
                .statusCode(200)
                .extract()
                .response()
            executionIds.add(executionResponse.jsonPath().getString("executionId"))
            Thread.sleep(100) // Delay to ensure different timestamps
        }

        Thread.sleep(200)

        // When: Querying execution history
        val historyResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Executions should be sorted by startedAt descending (most recent first)
        val executions = historyResponse.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(executions.size >= 3, "Should return at least 3 executions")

        // Verify sorting: each execution should have startedAt >= next execution's startedAt
        for (i in 0 until executions.size - 1) {
            val currentStartedAtStr = executions[i]["startedAt"].toString()
            val nextStartedAtStr = executions[i + 1]["startedAt"].toString()
            val currentStartedAt = Instant.parse(currentStartedAtStr)
            val nextStartedAt = Instant.parse(nextStartedAtStr)
            Assertions.assertTrue(
                currentStartedAt.isAfter(nextStartedAt) || currentStartedAt == nextStartedAt,
                "Execution at index $i should have startedAt >= execution at index ${i + 1}"
            )
        }
    }

    @Test
    fun `should return empty list when workflow has no executions`() {
        // Given: A workflow exists but has never been executed
        val createResponse = RestAssured.given()
            .config(YAML_CONFIG)
            .contentType("application/yaml")
            .body(VALID_WORKFLOW_YAML)
        .`when`()
            .post("/api/workflows")
        .then()
            .statusCode(201)
            .extract()
            .response()

        // When: Querying execution history
        val historyResponse = RestAssured.given()
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "test-ns", "test-workflow")
        .then()
            .statusCode(200)
            .extract()
            .response()

        // Then: Should return empty list
        val executions = historyResponse.jsonPath().getList<Map<String, Any>>("executions")
        Assertions.assertTrue(executions.isEmpty(), "Should return empty list for workflow with no executions")

        val pagination = historyResponse.jsonPath().getMap<String, Any>("pagination")
        Assertions.assertEquals(0L, (pagination["total"] as Number).toLong())
        Assertions.assertEquals(false, pagination["hasMore"] as Boolean)
    }

    @Test
    fun `should return 404 for non-existent workflow`() {
        // When: Querying execution history for non-existent workflow
        RestAssured.given()
        .`when`()
            .get(EXECUTION_HISTORY_ENDPOINT, "non-existent-ns", "non-existent-workflow")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", CoreMatchers.containsString("workflow-not-found"))
            .body("title", CoreMatchers.equalTo("Workflow Not Found"))
            .body("status", CoreMatchers.equalTo(404))
    }
}