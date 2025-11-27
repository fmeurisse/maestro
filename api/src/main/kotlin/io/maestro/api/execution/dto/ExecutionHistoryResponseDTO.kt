package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response DTO for execution history queries.
 *
 * Contains a paginated list of execution summaries with pagination metadata
 * and hypermedia links for navigation.
 */
data class ExecutionHistoryResponseDTO(
    @JsonProperty("executions")
    val executions: List<ExecutionSummaryDTO>,

    @JsonProperty("pagination")
    val pagination: PaginationDTO,

    @JsonProperty("_links")
    val links: Map<String, LinkDTO>
)
