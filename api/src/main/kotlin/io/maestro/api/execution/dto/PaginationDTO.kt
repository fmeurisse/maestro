package io.maestro.api.execution.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Pagination metadata DTO.
 *
 * Provides information about pagination state for list responses,
 * including total count, current page size, offset, and whether more results are available.
 */
data class PaginationDTO(
    @JsonProperty("total")
    val total: Long,

    @JsonProperty("limit")
    val limit: Int,

    @JsonProperty("offset")
    val offset: Int,

    @JsonProperty("hasMore")
    val hasMore: Boolean
) {
    companion object {
        fun create(
            total: Long,
            limit: Int,
            offset: Int
        ): PaginationDTO {
            return PaginationDTO(
                total = total,
                limit = limit,
                offset = offset,
                hasMore = (offset + limit) < total
            )
        }
    }
}
