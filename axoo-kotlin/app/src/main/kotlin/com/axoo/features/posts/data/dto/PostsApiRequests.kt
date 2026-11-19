package com.axoo.features.posts.data.dto

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.ImageMeta
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NewFeedSeenRequest(@Json(name = "seen_through") val seenThrough: String)

@JsonClass(generateAdapter = true)
data class VoteRequest(val value: Int)

@JsonClass(generateAdapter = true)
data class CreatePostRequest(
    val body: String,
    val entities: List<MessageEntity> = emptyList(),
    val flair: String? = null,
    val media: ImageMeta? = null,
    
    @Json(name = "client_id") val clientId: String,
    val poll: CreatePollRequest? = null,
)

@JsonClass(generateAdapter = true)
data class UpdatePostRequest(
    val body: String,
    val entities: List<MessageEntity> = emptyList(),
    val flair: String? = null,
    val media: ImageMeta? = null,
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    val media: ImageMeta? = null,
    @Json(name = "client_id") val clientId: String,
    val body: String,
    val entities: List<MessageEntity> = emptyList(),
    @Json(name = "reply_to_comment_id") val replyToCommentId: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateCommentRequest(
    val body: String,
    val entities: List<MessageEntity> = emptyList(),
)
