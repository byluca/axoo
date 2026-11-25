package com.axoo.features.posts.domain

enum class RemoteMediaType {
    Gif,
    Sticker,
    Meme,
}

data class RemoteMedia(
    val id: String,
    val previewUrl: String,
    val mediaUrl: String,
    val width: Int,
    val height: Int,
    val title: String,
    val type: RemoteMediaType,
    val previewFallbackUrl: String? = null,
    val loadingPreviewUrl: String? = null,
    val provider: String = "giphy",
    val searchQuery: String = "",
    val mimeType: String? = null,
)
