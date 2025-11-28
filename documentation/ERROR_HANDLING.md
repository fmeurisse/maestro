# Maestro Error Handling Guide

Last updated: 2025-11-27

This document describes error handling patterns, exception hierarchy, and RFC 7807 Problem Details implementation in the Maestro workflow orchestration system.

## Table of Contents
- Overview
- RFC 7807 Problem Details
- Exception Hierarchy
- Domain Exceptions
- API Error Responses
- Exception Mappers
- Error Context
- Best Practices
- Testing Errors

---

## Overview

Maestro uses a layered approach to error handling:

1. **Domain Layer (model)**: Domain exceptions for business rule violations
2. **Business Logic Layer (core)**: Use case exceptions and validation errors
3. **API Layer (api)**: HTTP status codes and RFC 7807 Problem Details

All API errors follow the RFC 7807 Problem Details specification for consistent, machine-readable error responses.

---

## RFC 7807 Problem Details

### Specification

RFC 7807 defines a standard format for HTTP API error responses using the `application/problem+json` media type.

**Standard Fields:**
- `type` (string): URI reference identifying the problem type
- `title` (string): Short, human-readable summary
- `status` (integer): HTTP status code
- `detail` (string): Detailed explanation specific to this occurrence
- `instance` (string, optional): URI reference for this specific occurrence

### Maestro Implementation

All Maestro API errors return `application/problem+json` responses:

```json
{
  "type": "https://maestro/errors/workflow-revision-not-found",
  "title": "Workflow revision not found",
  "status": 404,
  "detail": "Revision 1 for namespace test-ns workflow my-workflow not found"
}
```

**Type URI Format:**
- Base: `https://maestro/errors/`
- Suffix: Kebab-case error identifier
- Examples:
  - `https://maestro/errors/workflow-revision-not-found`
  - `https://maestro/errors/parameter-validation-error`
  - `https://maestro/errors/execution-not-found`

---

## Exception Hierarchy

### Domain Exceptions (core module)

All domain exceptions extend base exception classes and include structured error information.

**Base Exception:**
```kotlin
abstract class MaestroException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

**Exception Categories:**

1. **Not Found Exceptions** (404)
   - `WorkflowRevisionNotFoundException`
   - `WorkflowNotFoundException`
   - `ExecutionNotFoundException`

2. **Validation Exceptions** (400)
   - `ParameterValidationException`

3. **State Conflict Exceptions** (409)
   - `RevisionNotActiveException`
   - `ActiveRevisionException`

---

## Domain Exceptions

### WorkflowRevisionNotFoundException

Thrown when a workflow revision does not exist.

**Usage:**
- Loading workflow revision for execution
- Retrieving workflow revision by ID
- Activating/deactivating non-existent revision

**Properties:**
- `revisionId: WorkflowRevisionID` - The missing revision identifier

**HTTP Mapping:**
- Status: `404 Not Found`
- Type: `https://maestro/errors/workflow-revision-not-found`

**Example:**
```kotlin
class WorkflowRevisionNotFoundException(
    val revisionId: WorkflowRevisionID
) : MaestroException("Revision ${revisionId.version} for ${revisionId.namespace}/${revisionId.workflowId} not found")

// Usage
throw WorkflowRevisionNotFoundException(
    WorkflowRevisionID("test-ns", "my-workflow", 1)
)
```

**Response:**
```json
{
  "type": "https://maestro/errors/workflow-revision-not-found",
  "title": "Workflow revision not found",
  "status": 404,
  "detail": "Revision 1 for test-ns/my-workflow not found"
}
```

---

### ExecutionNotFoundException

Thrown when an execution does not exist.

**Usage:**
- Querying execution status by ID
- Invalid execution ID in API request

**Properties:**
- `executionId: String` - The missing execution identifier

**HTTP Mapping:**
- Status: `404 Not Found`
- Type: `https://maestro/errors/execution-not-found`

**Example:**
```kotlin
class ExecutionNotFoundException(
    val executionId: String
) : MaestroException("Execution $executionId not found")

// Usage
throw ExecutionNotFoundException("abc123xyz456def789ghi")
```

**Response:**
```json
{
  "type": "https://maestro/errors/execution-not-found",
  "title": "Execution not found",
  "status": 404,
  "detail": "Execution abc123xyz456def789ghi not found"
}
```

---

### ParameterValidationException

Thrown when input parameters fail validation.

**Usage:**
- Executing workflow with invalid parameters
- Type mismatch
- Missing required parameters
- Extra parameters not defined in schema

**Properties:**
- `revisionId: WorkflowRevisionID` - The workflow revision being executed
- `validationResult: ParameterValidationResult` - Detailed validation errors

**HTTP Mapping:**
- Status: `400 Bad Request`
- Type: `https://maestro/errors/parameter-validation-error`

**Example:**
```kotlin
data class ParameterValidationResult(
    val errors: List<ParameterError>
) {
    fun isValid(): Boolean = errors.isEmpty()
}

sealed class ParameterError {
    data class TypeMismatch(val name: String, val expected: String, val actual: String) : ParameterError()
    data class MissingRequired(val name: String) : ParameterError()
    data class ExtraParameter(val name: String) : ParameterError()
}

class ParameterValidationException(
    val revisionId: WorkflowRevisionID,
    val validationResult: ParameterValidationResult
) : MaestroException("Parameter validation failed for ${revisionId}: ${validationResult.errors.joinToString()}")

// Usage
throw ParameterValidationException(
    revisionId = WorkflowRevisionID("test-ns", "my-workflow", 1),
    validationResult = ParameterValidationResult(listOf(
        ParameterError.TypeMismatch("age", "integer", "string"),
        ParameterError.MissingRequired("userName")
    ))
)
```

**Response:**
```json
{
  "type": "https://maestro/errors/parameter-validation-error",
  "title": "Parameter validation failed",
  "status": 400,
  "detail": "Validation failed: Parameter 'age' expected type 'integer' but got 'string'; Missing required parameter 'userName'",
  "errors": [
    {
      "type": "TYPE_MISMATCH",
      "parameter": "age",
      "expected": "integer",
      "actual": "string"
    },
    {
      "type": "MISSING_REQUIRED",
      "parameter": "userName"
    }
  ]
}
```

---

### ActiveRevisionException

Thrown when attempting to delete an active workflow revision.

**Usage:**
- Deleting an active revision without deactivating first
- Deleting a workflow with active revisions

**Properties:**
- `revisionId: WorkflowRevisionID` - The active revision that cannot be deleted

**HTTP Mapping:**
- Status: `409 Conflict`
- Type: `https://maestro/errors/active-revision-cannot-be-deleted`

**Example:**
```kotlin
class ActiveRevisionException(
    val revisionId: WorkflowRevisionID
) : MaestroException("Cannot delete active revision: $revisionId")

// Usage
throw ActiveRevisionException(
    WorkflowRevisionID("test-ns", "my-workflow", 1)
)
```

**Response:**
```json
{
  "type": "https://maestro/errors/active-revision-cannot-be-deleted",
  "title": "Cannot delete active revision",
  "status": 409,
  "detail": "Revision 1 for test-ns/my-workflow is active and cannot be deleted. Deactivate it first."
}
```

---

### RevisionNotActiveException

Thrown when attempting to execute an inactive workflow revision.

**Usage:**
- Executing a workflow revision that hasn't been activated
- Ensuring only active revisions can be executed (future requirement)

**Properties:**
- `revisionId: WorkflowRevisionID` - The inactive revision

**HTTP Mapping:**
- Status: `409 Conflict`
- Type: `https://maestro/errors/revision-not-active`

**Example:**
```kotlin
class RevisionNotActiveException(
    val revisionId: WorkflowRevisionID
) : MaestroException("Revision $revisionId is not active")

// Usage
throw RevisionNotActiveException(
    WorkflowRevisionID("test-ns", "my-workflow", 2)
)
```

**Response:**
```json
{
  "type": "https://maestro/errors/revision-not-active",
  "title": "Revision not active",
  "status": 409,
  "detail": "Revision 2 for test-ns/my-workflow is not active. Activate it before executing."
}
```

---

## API Error Responses

### Standard HTTP Status Codes

Maestro uses standard HTTP status codes:

**Success (2xx):**
- `200 OK` - Successful retrieval or update
- `201 Created` - Successful creation
- `204 No Content` - Successful deletion

**Client Errors (4xx):**
- `400 Bad Request` - Invalid input, validation errors
- `404 Not Found` - Resource not found
- `409 Conflict` - State constraint violation

**Server Errors (5xx):**
- `500 Internal Server Error` - Unexpected server error

### Error Response Structure

All 4xx and 5xx responses include `application/problem+json` body:

```json
{
  "type": "https://maestro/errors/{error-type}",
  "title": "Human-readable title",
  "status": 400,
  "detail": "Detailed error message for this occurrence",
  "instance": "/api/workflows/test-ns/my-workflow/1",  // Optional
  // Additional fields specific to error type
}
```

---

## Exception Mappers

### Quarkus Exception Mappers

Exception mappers convert domain exceptions to RFC 7807 responses.

**Mapper Interface:**
```kotlin
@Provider
class WorkflowRevisionNotFoundExceptionMapper : ExceptionMapper<WorkflowRevisionNotFoundException> {
    override fun toResponse(exception: WorkflowRevisionNotFoundException): Response {
        val problem = Problem(
            type = URI("https://maestro/errors/workflow-revision-not-found"),
            title = "Workflow revision not found",
            status = 404,
            detail = exception.message
        )
        return Response.status(404)
            .type("application/problem+json")
            .entity(problem)
            .build()
    }
}
```

**Registered Mappers:**

1. `WorkflowRevisionNotFoundExceptionMapper` → 404
2. `ExecutionNotFoundExceptionMapper` → 404
3. `ParameterValidationExceptionMapper` → 400
4. `ActiveRevisionExceptionMapper` → 409
5. `RevisionNotActiveExceptionMapper` → 409
6. `GenericExceptionMapper` → 500 (catch-all for unexpected errors)

**Generic Mapper (Catch-All):**
```kotlin
@Provider
class GenericExceptionMapper : ExceptionMapper<Exception> {
    override fun toResponse(exception: Exception): Response {
        // Log the exception for debugging
        logger.error("Unexpected error", exception)

        val problem = Problem(
            type = URI("https://maestro/errors/internal-server-error"),
            title = "Internal server error",
            status = 500,
            detail = "An unexpected error occurred. Please contact support."
            // Do NOT include exception details in production
        )
        return Response.status(500)
            .type("application/problem+json")
            .entity(problem)
            .build()
    }
}
```

---

## Error Context

### Step-Level Error Information

When a workflow step fails, detailed error information is captured in `ErrorInfo`:

**Structure:**
```kotlin
data class ErrorInfo(
    val errorType: String,        // Exception class name
    val stackTrace: String,        // Full stack trace
    val stepInputs: Map<String, Any>  // Input values that caused error
)
```

**Storage:**
Stored in `execution_step_results.error_details` as JSONB:

```json
{
  "errorType": "java.lang.IllegalArgumentException",
  "stackTrace": "java.lang.IllegalArgumentException: Count must be positive\n\tat ...",
  "stepInputs": {
    "count": -1,
    "userName": "Alice"
  }
}
```

**Usage:**
- Debugging failed executions
- Reproducing errors with same inputs
- Root cause analysis

---

### Execution-Level Error Information

When a workflow execution fails, the error message is stored at the execution level:

**Fields:**
- `errorMessage: String` - High-level error description
- `completedAt: Instant` - Timestamp of failure

**Example:**
```kotlin
WorkflowExecution(
    executionId = "abc123",
    status = ExecutionStatus.FAILED,
    errorMessage = "Step 'validate-input' failed: Count must be positive",
    completedAt = Instant.now()
)
```

**API Response:**
```json
{
  "executionId": "abc123",
  "status": "FAILED",
  "errorMessage": "Step 'validate-input' failed: Count must be positive",
  "completedAt": "2025-11-27T10:05:00Z",
  "steps": [
    {
      "stepIndex": 0,
      "stepId": "validate-input",
      "status": "FAILED",
      "errorMessage": "Count must be positive",
      "errorDetails": {
        "errorType": "java.lang.IllegalArgumentException",
        "stackTrace": "...",
        "stepInputs": {"count": -1}
      }
    }
  ]
}
```

---

## Best Practices

### 1. Use Specific Exceptions

Always throw the most specific exception type:

**Good:**
```kotlin
throw ParameterValidationException(revisionId, validationResult)
```

**Bad:**
```kotlin
throw RuntimeException("Invalid parameters")
```

### 2. Include Context in Exceptions

Provide enough context for debugging:

**Good:**
```kotlin
throw WorkflowRevisionNotFoundException(
    WorkflowRevisionID(namespace, workflowId, version)
)
```

**Bad:**
```kotlin
throw Exception("Workflow not found")
```

### 3. Don't Leak Internal Details

Never expose internal implementation details, stack traces, or sensitive data in production API responses:

**Good:**
```json
{
  "detail": "Parameter validation failed"
}
```

**Bad:**
```json
{
  "detail": "NullPointerException at com.internal.SecretClass.method(SecretClass.kt:42)"
}
```

### 4. Log Errors Appropriately

Log unexpected errors with full context:

```kotlin
try {
    executeWorkflow(...)
} catch (e: ParameterValidationException) {
    // Expected error, log at INFO level
    logger.info("Parameter validation failed for execution", e)
    throw e
} catch (e: Exception) {
    // Unexpected error, log at ERROR level with full stack trace
    logger.error("Unexpected error during workflow execution", e)
    throw e
}
```

### 5. Test Error Paths

Always test error scenarios:

```kotlin
@Test
fun `execute workflow with invalid parameters returns 400`() {
    // Given
    val request = ExecutionRequest(
        namespace = "test-ns",
        id = "my-workflow",
        version = 1,
        parameters = mapOf("age" to "not-a-number")  // Invalid type
    )

    // When
    val response = given()
        .contentType(ContentType.JSON)
        .body(request)
        .post("/api/executions")

    // Then
    response
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("type", equalTo("https://maestro/errors/parameter-validation-error"))
        .body("title", equalTo("Parameter validation failed"))
        .body("status", equalTo(400))
}
```

---

## Testing Errors

### Unit Testing Domain Exceptions

Test exception creation and message formatting:

```kotlin
@Test
fun `WorkflowRevisionNotFoundException includes revision ID in message`() {
    // Given
    val revisionId = WorkflowRevisionID("test-ns", "my-workflow", 1)

    // When
    val exception = WorkflowRevisionNotFoundException(revisionId)

    // Then
    assertThat(exception.message).contains("test-ns")
    assertThat(exception.message).contains("my-workflow")
    assertThat(exception.message).contains("1")
    assertThat(exception.revisionId).isEqualTo(revisionId)
}
```

### Integration Testing API Error Responses

Test that exceptions are properly mapped to RFC 7807 responses:

```kotlin
@Test
fun `GET non-existent execution returns 404 with problem details`() {
    given()
        .get("/api/executions/nonexistent123456789")
        .then()
        .statusCode(404)
        .contentType("application/problem+json")
        .body("type", equalTo("https://maestro/errors/execution-not-found"))
        .body("title", equalTo("Execution not found"))
        .body("status", equalTo(404))
        .body("detail", containsString("nonexistent123456789"))
}
```

### Testing Validation Errors

Test parameter validation with various error scenarios:

```kotlin
@Test
fun `execute workflow with missing required parameter returns 400`() {
    // Workflow requires "userName" parameter
    val request = ExecutionRequest(
        namespace = "test-ns",
        id = "my-workflow",
        version = 1,
        parameters = emptyMap()  // Missing userName
    )

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .post("/api/executions")
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("type", equalTo("https://maestro/errors/parameter-validation-error"))
        .body("errors[0].type", equalTo("MISSING_REQUIRED"))
        .body("errors[0].parameter", equalTo("userName"))
}
```

---

## Error Catalog

### Complete Error Reference

| Exception | HTTP Status | Type URI | When Thrown |
|-----------|-------------|----------|-------------|
| WorkflowRevisionNotFoundException | 404 | workflow-revision-not-found | Workflow revision doesn't exist |
| ExecutionNotFoundException | 404 | execution-not-found | Execution ID doesn't exist |
| WorkflowNotFoundException | 404 | workflow-not-found | Workflow doesn't exist (any version) |
| ParameterValidationException | 400 | parameter-validation-error | Input parameter validation fails |
| ActiveRevisionException | 409 | active-revision-cannot-be-deleted | Attempting to delete active revision |
| RevisionNotActiveException | 409 | revision-not-active | Executing inactive revision |
| GenericException | 500 | internal-server-error | Unexpected server error |

---

## Related Documentation

- Architecture: `ARCHITECTURE.md`
- API User Guide: `API_USER_GUIDE.md`
- Data Model: `DATA_MODEL.md`
- Developer Guide: `DEVELOPER_GUIDE.md`

---

If you find inconsistencies or missing details, please open an issue or submit a PR updating `documentation/ERROR_HANDLING.md`.
