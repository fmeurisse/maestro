package io.maestro.model.execution

import com.fasterxml.jackson.annotation.JsonProperty
import io.maestro.model.Link
import io.maestro.model.Pagination

/**
 * Response for execution history queries.
 *
 * Contains a paginated list of execution summaries with pagination metadata
 * and hypermedia links for navigation.
 */
data class ExecutionHistoryResponse(
    @param:JsonProperty("executions")
    val executions: List<ExecutionSummary>,

    @param:JsonProperty("pagination")
    val pagination: Pagination,

    @param:JsonProperty("_links")
    val links: Map<String, Link>
)