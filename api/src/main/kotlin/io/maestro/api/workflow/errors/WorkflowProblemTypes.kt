package io.maestro.api.workflow.errors

import java.net.URI

/**
 * Constants for RFC 7807 problem type URIs used in workflow management.
 * 
 * These URIs identify specific problem types in JSON Problem responses.
 * Following RFC 7807, problem types should be URIs that uniquely identify
 * the problem category.
 * 
 * All problem types use the base URI: https://maestro.io/problems/
 */
object WorkflowProblemTypes {
    
    /**
     * Problem type for when a workflow revision cannot be found.
     * Status: 404 Not Found
     */
    val WORKFLOW_NOT_FOUND: URI = URI.create("https://maestro.io/problems/workflow-not-found")
    
    /**
     * Problem type for when attempting to create a workflow that already exists.
     * Status: 409 Conflict
     */
    val WORKFLOW_ALREADY_EXISTS: URI = URI.create("https://maestro.io/problems/workflow-exists")
    
    /**
     * Problem type for when attempting to modify or delete an active revision.
     * Active revisions must be deactivated first.
     * Status: 409 Conflict
     */
    val ACTIVE_REVISION_CONFLICT: URI = URI.create("https://maestro.io/problems/active-revision-conflict")
    
    /**
     * Problem type for YAML parsing errors.
     * Status: 400 Bad Request
     */
    val INVALID_YAML: URI = URI.create("https://maestro.io/problems/invalid-yaml")
    
    /**
     * Problem type for invalid or unknown workflow step types.
     * Status: 400 Bad Request
     */
    val INVALID_STEP: URI = URI.create("https://maestro.io/problems/invalid-step")
    
    /**
     * Problem type for field validation failures.
     * Status: 400 Bad Request
     */
    val VALIDATION_ERROR: URI = URI.create("https://maestro.io/problems/validation-error")
}
