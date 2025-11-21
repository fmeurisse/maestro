package io.maestro.model

import io.maestro.model.WorkflowID.Companion.parse
import io.maestro.model.exception.MalformedWorkflowIDException

/**
 * Immutable identity of a workflow across all its revisions.
 *
 * A workflow is uniquely addressed by the pair `namespace:id`.
 * Both [namespace] and [id] must be non-blank; this is enforced at construction time.
 *
 * Typical uses:
 * - Human-readable form via [toString] → `"<namespace>:<id>"`.
 * - Create a revision-scoped identifier via [withVersion].
 * - Parse from the canonical string form using [parse].
 *
 * @property namespace Logical isolation boundary (e.g., environment or tenant)
 * @property id Workflow identifier within the given [namespace]
 */
data class WorkflowID(
    override val namespace: String,
    override val id: String
) : IWorkflowID {
    init {
        if (namespace.isBlank()) {
            throw MalformedWorkflowIDException(
                "$namespace:$id",
                "Namespace must not be blank"
            )
        }
        if (id.isBlank()) {
            throw MalformedWorkflowIDException(
                "$namespace:$id",
                "ID must not be blank"
            )
        }
    }

    /**
     * Returns a revision-scoped ID for the given [version].
     *
     * Note: Callers are responsible for supplying a valid, positive version according
     * to their versioning scheme; no range checks are performed here.
     */
    fun withVersion(version: Int): WorkflowRevisionID =
        WorkflowRevisionID(namespace, id, version)

    /** Canonical string form: `"<namespace>:<id>"`. */
    override fun toString(): String = "$namespace:$id"

    companion object {
        /**
         * Parses a [WorkflowID] from its canonical string form `"namespace:id"`.
         *
         * Examples:
         * - `WorkflowID.parse("production:payment-workflow")`
         * - `WorkflowID.parse("ns123:id456")`
         *
         * @throws MalformedWorkflowIDException if the input is not exactly two parts
         * separated by a single colon, or if either part is blank.
         */
        fun parse(str: String): WorkflowID {
            val parts = str.split(":")
            if (parts.size != 2) {
                throw MalformedWorkflowIDException(str, "Invalid WorkflowID format: $str (expected namespace:id)")
            }

            val namespace = parts[0]
            val id = parts[1]

            if (namespace.isBlank()) {
                throw MalformedWorkflowIDException(str, "Namespace must not be blank in WorkflowID: $str")
            }

            if (id.isBlank()) {
                throw MalformedWorkflowIDException(str, "ID must not be blank in WorkflowID: $str")
            }

            return WorkflowID(
                namespace = namespace,
                id = id
            )
        }
    }
}