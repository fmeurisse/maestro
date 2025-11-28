package io.maestro.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Pagination metadata.
 *
 * Provides information about pagination state for list responses,
 * including total count, current page size, offset, and whether more results are available.
 */
data class Pagination(
    @param:JsonProperty("total")
    val total: Long,

    @param:JsonProperty("limit")
    val limit: Int,

    @param:JsonProperty("offset")
    val offset: Int,

    @param:JsonProperty("hasMore")
    val hasMore: Boolean
) {
    companion object {
        fun create(
            total: Long,
            limit: Int,
            offset: Int
        ): Pagination {
            return Pagination(
                total = total,
                limit = limit,
                offset = offset,
                hasMore = (offset + limit) < total
            )
        }
    }
}