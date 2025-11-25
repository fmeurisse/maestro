package io.maestro.model

/**
 * Minimal identity of a workflow independent of its revisions.
 *
 * A workflow is uniquely addressed by the pair `namespace:id`.
 * Implementations should validate that both components are non-blank.
 *
 * Common string representation used across the codebase is `"<namespace>:<id>"`.
 */
interface IWorkflowID {
    /** Logical isolation boundary, e.g. an environment or tenant. */
    val namespace: String
    /** Identifier of the workflow within the given [namespace]. */
    val id: String
}