package io.maestro.model

/**
 * Identity of a specific workflow revision.
 *
 * Extends [IWorkflowID] by adding a monotonically increasing [version]
 * that selects a concrete revision of the workflow identified by
 * `namespace:id`.
 *
 * Contract:
 * - [version] must be a positive value (1-based) in typical implementations.
 */
interface IWorkflowRevisionID : IWorkflowID {
    /** Numeric revision identifier for the workflow; commonly 1-based. */
    val version: Int
}