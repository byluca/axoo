package com.axoo.features.posts.domain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImageMeta(
    val url: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val type: String,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "preview_fallback_url") val previewFallbackUrl: String? = null,
    val provider: String? = null,
    @Json(name = "provider_id") val providerId: String? = null,
)
