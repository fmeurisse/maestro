package io.maestro.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * HATEOAS link.
 */
data class Link(
    @JsonProperty("href")
    val href: String
)