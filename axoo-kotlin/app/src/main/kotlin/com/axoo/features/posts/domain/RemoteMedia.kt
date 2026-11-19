package com.axoo.features.posts.domain

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class RemoteMediaType {
    Gif,
    GiphySticker,
    Sticker,
}

@JsonClass(generateAdapter = true)
data class RemoteMedia(
    val id: String,
    val previewUrl: String,
    val mediaUrl: String,
    val width: Int,
    val height: Int,
    val title: String,
    val type: RemoteMediaType,
    val category: String? = null,
    val previewFallbackUrl: String? = null,
    val loadingPreviewUrl: String? = null,
    val provider: String = "giphy",
    val searchQuery: String = "",
    val mimeType: String? = null,
)

data class RemoteMediaCategory(
    val id: String,
    val label: String,
    val iconAsset: String = "",
)
