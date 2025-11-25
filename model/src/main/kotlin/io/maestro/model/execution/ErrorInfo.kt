package io.maestro.model.execution

/**
 * Structured error details for failed steps.
 * 
 * Contains comprehensive error information for debugging and auditing:
 * - Exception type and stack trace
 * - Input values that caused the error (for reproduction)
 * 
 * Stored as JSONB in database for flexible querying.
 * Sensitive data (passwords, API keys) should be filtered before persistence.
 */
data class ErrorInfo(
    /**
     * Exception class name (e.g., "NullPointerException", "TimeoutException")
     */
    val errorType: String,
    
    /**
     * Full stack trace for debugging
     */
    val stackTrace: String,
    
    /**
     * Input values that caused the error (for reproduction).
     * Null if inputs are not available or not relevant.
     */
    val stepInputs: Map<String, Any>? = null
) {
    init {
        require(errorType.isNotBlank()) { "ErrorInfo.errorType must not be blank" }
        require(stackTrace.isNotBlank()) { "ErrorInfo.stackTrace must not be blank" }
    }
}
