package io.maestro.api.dto

import io.maestro.model.steps.Step
import java.time.Instant

/**
 * Response DTO for workflow revision (used for API responses with YAML source).
 * Maps to YAML output structure per API Contract.
 */
data class WorkflowRevisionResponse(
    val namespace: String,
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val active: Boolean,
    val rootStep: Step,
    val createdAt: Instant,
    val updatedAt: Instant
)
