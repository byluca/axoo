package com.axoo.features.posts.data.dto

import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.VoteResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PostsResponse(
    val posts: List<Post>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
)

@JsonClass(generateAdapter = true)
data class NewFeedUnreadCountResponse(
    @Json(name = "unread_count") val unreadCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PostResponse(val post: Post)

@JsonClass(generateAdapter = true)
data class CommentsResponse(val comments: List<Comment>)

@JsonClass(generateAdapter = true)
data class CommentResponse(
    val comment: Comment,
    val created: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class VotePayload(
    val upvotes: Int,
    val downvotes: Int,
    @Json(name = "my_vote") val myVote: String = "",
)

@JsonClass(generateAdapter = true)
data class VoteResponse(val vote: VotePayload)

internal fun VotePayload.toDomain() = VoteResult(upvotes, downvotes, myVote)
