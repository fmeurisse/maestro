package io.maestro.core.errors

import io.maestro.model.WorkflowRevisionID
import io.maestro.model.errors.MaestroException

/**
 * Thrown when attempting to modify or delete an active revision.
 * Active revisions must be deactivated before they can be updated or deleted.
 * Maps to 409 Conflict.
 *
 * @property operation The operation that was attempted (e.g., "update", "delete")
 */
class ActiveRevisionConflictException(id: WorkflowRevisionID, operation: String) :
    MaestroException(
        type = "/problems/active-revision-conflict",
        title = "Active Revision Conflict",
        status = 409,
        message = "Cannot $operation active revision: $id. Deactivate it first.",
        instance = null
    )