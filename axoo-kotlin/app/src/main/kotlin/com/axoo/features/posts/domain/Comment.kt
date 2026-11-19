package com.axoo.features.posts.domain

import com.axoo.engine.emoji.text.MessageEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Comment(
    val id: String,
    val media: ImageMeta? = null,
    @Json(name = "post_id") val postId: String,
    @Json(name = "client_id") val clientId: String? = null,
    @Json(name = "parent_comment_id") val parentCommentId: String? = null,
    @Json(name = "user_id") val userId: String,
    val nickname: String = "",
    @Json(name = "university_name") val universityName: String = "",
    val body: String = "",
    @Json(name = "is_deleted") val isDeleted: Boolean = false,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val entities: List<MessageEntity> = emptyList(),
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "my_vote") val myVoteRaw: String? = null,
    @Json(name = "avatar_config") val avatarConfig: Map<String, Any>? = null,
) {
    val myVote: VoteState get() = VoteState.fromString(myVoteRaw)
    val canEdit: Boolean get() = !isDeleted && !id.startsWith("local_") && media?.type != "sticker"
}
