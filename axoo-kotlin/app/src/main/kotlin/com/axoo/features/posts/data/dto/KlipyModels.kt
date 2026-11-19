package com.axoo.features.posts.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KlipyResponse(val result: Boolean, val data: KlipyPage)

@JsonClass(generateAdapter = true)
data class KlipyPage(
    val data: List<KlipyItem>,
    @Json(name = "current_page") val currentPage: Int,
    @Json(name = "has_next") val hasNext: Boolean,
)

@JsonClass(generateAdapter = true)
data class KlipyItem(
    val slug: String = "",
    val title: String = "",
    val type: String,
    val file: Map<String, Map<String, KlipyFile>> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class KlipyFile(val url: String, val width: Int, val height: Int)

@JsonClass(generateAdapter = true)
data class KlipyShareRequest(@Json(name = "customer_id") val customerId: String, val q: String)
