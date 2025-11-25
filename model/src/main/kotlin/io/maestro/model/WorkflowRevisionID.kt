package io.maestro.model

import io.maestro.model.errors.MalformedWorkflowRevisionIDException

/**
 * Composite identifier for a workflow revision.
 * Combination of namespace + id + version uniquely identifies a revision.
 *
 * @property namespace Logical isolation boundary (e.g., "production", "staging")
 * @property id Workflow identifier within namespace
 * @property version Sequential version number (1, 2, 3...)
 */
data class WorkflowRevisionID(
    override val namespace: String,
    override val id: String,
    override val version: Int
) : IWorkflowRevisionID {
    init {
        if (namespace.isBlank()) {
            throw MalformedWorkflowRevisionIDException(
                "$namespace:$id:$version",
                "Namespace must not be blank"
            )
        }
        if (id.isBlank()) {
            throw MalformedWorkflowRevisionIDException(
                "$namespace:$id:$version",
                "ID must not be blank"
            )
        }
        if (version <= 0) {
            throw MalformedWorkflowRevisionIDException(
                "$namespace:$id:$version",
                "Version must be positive, got: $version"
            )
        }
    }

    /**
     * Canonical string form: `"<namespace>:<id>:<version>"`.
     */
    override fun toString(): String = "$namespace:$id:$version"

    companion object {
        /**
         * Parse a WorkflowRevisionID from string format "namespace:id:version"
         * 
         * @throws MalformedWorkflowRevisionIDException if the string format is invalid
         */
        fun parse(str: String): WorkflowRevisionID {
            val parts = str.split(":")
            if (parts.size != 3) {
                throw MalformedWorkflowRevisionIDException(
                    str,
                    "Invalid WorkflowRevisionID format: $str (expected namespace:id:version)"
                )
            }
            
            val namespace = parts[0]
            val id = parts[1]
            val versionStr = parts[2]
            
            if (namespace.isBlank()) {
                throw MalformedWorkflowRevisionIDException(
                    str,
                    "Namespace must not be blank in WorkflowRevisionID: $str"
                )
            }
            
            if (id.isBlank()) {
                throw MalformedWorkflowRevisionIDException(
                    str,
                    "ID must not be blank in WorkflowRevisionID: $str"
                )
            }
            
            val version = versionStr.toIntOrNull()
                ?: throw MalformedWorkflowRevisionIDException(
                    str,
                    "Invalid version number in WorkflowRevisionID: $str (version must be a positive integer, got: $versionStr)"
                )
            
            if (version <= 0) {
                throw MalformedWorkflowRevisionIDException(
                    str,
                    "Version must be positive in WorkflowRevisionID: $str (got: $version)"
                )
            }
            
            return WorkflowRevisionID(
                namespace = namespace,
                id = id,
                version = version
            )
        }
    }
}
