package io.maestro.model.execution

import io.maestro.model.errors.MalformedWorkflowExecutionIDException
import io.maestro.model.util.DEFAULT_SIZE
import io.maestro.model.util.NanoID

/**
 * Value object wrapping a NanoID identifier for workflow execution.
 * 
 * NanoID provides:
 * - Global uniqueness (cryptographically strong)
 * - URL-safe format (no special characters that need encoding)
 * - Shorter than UUID (21 characters vs 36)
 * - Zero coordination overhead (no database roundtrips, sequences, or locks)
 * 
 * API Format: 21-character URL-safe string
 * Example: "V1StGXR8_Z5jdHi6B-myT"
 */
@JvmInline
value class WorkflowExecutionID(val value: String) {
    init {
        if (value.isBlank()) throw MalformedWorkflowExecutionIDException(value, "ID must not be blank")
        if (!NanoID.isValid(value, minSize = 1, maxSize = 100)) throw MalformedWorkflowExecutionIDException(value, "Invalid NanoID format: $value (expected 21 characters, URL-safe)")

    }
    
    /**
     * Returns the NanoID as a string.
     * This format is used in REST API paths and responses.
     * 
     * Example: "V1StGXR8_Z5jdHi6B-myT"
     */
    override fun toString(): String = value
    
    companion object {
        /**
         * Parse a WorkflowExecutionID from API format (NanoID string).
         * 
         * @throws IllegalArgumentException if the string format is invalid
         */
        fun fromString(str: String): WorkflowExecutionID {
            return WorkflowExecutionID(str)
        }
        
        /**
         * Generate a new WorkflowExecutionID using NanoID.
         * 
         * Uses the default NanoID generator (21 characters, URL-safe).
         */
        fun generate(): WorkflowExecutionID {
            val nanoid = NanoID.generate()
            return WorkflowExecutionID(nanoid)
        }
    }
}
