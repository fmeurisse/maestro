package io.maestro.core.errors

import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.MaestroException
import java.time.Instant

/**
 * Exception thrown when an optimistic locking conflict is detected during concurrent updates.
 *
 * This occurs when two users attempt to update the same workflow revision simultaneously.
 * The update is rejected if the `updatedAt` timestamp in the YAML doesn't match the current
 * database value, indicating that another user has modified the revision in the meantime.
 *
 * **HTTP Status**: 409 Conflict
 * **RFC 7807 Type**: https://maestro.io/problems/optimistic-lock-conflict
 *
 * This exception is part of the core use case layer, as optimistic locking is a
 * use case concern rather than a pure domain model concern.
 *
 * @property revisionId The identifier of the workflow revision that experienced the conflict
 * @property expectedUpdatedAt The timestamp from the YAML (client's version)
 * @property actualUpdatedAt The current timestamp in the database (after another user's update)
 *
 * @see <a href="https://en.wikipedia.org/wiki/Optimistic_concurrency_control">Optimistic Concurrency Control</a>
 */
class OptimisticLockException(
    val revisionId: WorkflowRevisionID,
    val expectedUpdatedAt: Instant,
    val actualUpdatedAt: Instant
) : MaestroException(
    type = "https://maestro.io/problems/optimistic-lock-conflict",
    title = "Optimistic Lock Conflict",
    status = 409,
    message = buildDetailMessage(revisionId, expectedUpdatedAt, actualUpdatedAt),
    instance = "/api/workflows/${revisionId.namespace}/${revisionId.id}/${revisionId.version}"
) {
    companion object {
        private fun buildDetailMessage(
            revisionId: WorkflowRevisionID,
            expectedUpdatedAt: Instant,
            actualUpdatedAt: Instant
        ): String = """
            Workflow revision $revisionId has been modified by another user.
            Expected updatedAt: $expectedUpdatedAt
            Actual updatedAt: $actualUpdatedAt
            Please refresh the revision and retry your update.
        """.trimIndent()
    }
}
