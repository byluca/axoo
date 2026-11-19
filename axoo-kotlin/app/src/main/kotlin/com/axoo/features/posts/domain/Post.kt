package com.axoo.features.posts.domain

import com.axoo.engine.emoji.text.MessageEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Post(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val nickname: String = "",
    @Json(name = "university_id") val universityId: String,
    @Json(name = "university_name") val universityName: String = "",
    val flair: String = "",
    val body: String = "",
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    val media: ImageMeta? = null,
    val entities: List<MessageEntity> = emptyList(),
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "my_vote") val myVoteRaw: String? = null,
    @Json(name = "avatar_config") val avatarConfig: Map<String, Any>? = null,
    val poll: PostPoll? = null,
) {
    val myVote: VoteState get() = VoteState.fromString(myVoteRaw)
}

