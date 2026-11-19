package com.axoo.features.posts.presentation.postdetail

import com.axoo.features.posts.domain.Comment

enum class CommentDeliveryState {
    Sending,
    Sent,
}

data class CommentUiModel(
    val comment: Comment,
    val deliveryState: CommentDeliveryState = CommentDeliveryState.Sent,
    val localId: String? = null,
) {
    val id: String get() = comment.id
    val stableKey: String
        get() = comment.clientId?.takeIf(String::isNotBlank)
            ?: localId?.takeIf(String::isNotBlank)
            ?: comment.id
    val isOptimistic: Boolean get() = localId != null
}

internal fun CommentUiModel.hasSameRenderedBody(other: CommentUiModel): Boolean =
    stableKey == other.stableKey &&
        comment.media == other.comment.media && comment.body == other.comment.body &&
        comment.entities == other.comment.entities &&
        comment.isDeleted == other.comment.isDeleted
