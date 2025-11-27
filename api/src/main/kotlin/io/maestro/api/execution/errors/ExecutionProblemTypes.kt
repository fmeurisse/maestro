package io.maestro.api.execution.errors

/**
 * RFC 7807 Problem Type URIs for workflow execution errors.
 *
 * These URIs identify the problem types returned in application/problem+json responses.
 * Each URI should be documented in the API documentation with:
 * - Problem description
 * - Possible causes
 * - Recommended resolution steps
 */
object ExecutionProblemTypes {
    /**
     * Problem type for workflow not found errors (404).
     * Used when attempting to execute a non-existent workflow revision.
     */
    const val WORKFLOW_NOT_FOUND = "/problems/workflow-not-found"

    /**
     * Problem type for execution not found errors (404).
     * Used when querying status of a non-existent execution.
     */
    const val EXECUTION_NOT_FOUND = "/problems/execution-not-found"

    /**
     * Problem type for parameter validation errors (400).
     * Used when input parameters don't match the workflow schema.
     * Response includes 'invalid-params' array with detailed validation errors.
     */
    const val PARAMETER_VALIDATION_ERROR = "/problems/parameter-validation-error"

    /**
     * Problem type for execution failures (500).
     * Used when workflow execution fails due to step errors or system issues.
     */
    const val EXECUTION_FAILED = "/problems/execution-failed"

    /**
     * Problem type for execution timeouts (504).
     * Used when workflow execution exceeds the allowed time limit.
     */
    const val EXECUTION_TIMEOUT = "/problems/execution-timeout"
}
