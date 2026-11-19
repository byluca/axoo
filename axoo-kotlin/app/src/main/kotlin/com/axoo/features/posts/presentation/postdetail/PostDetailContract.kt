package com.axoo.features.posts.presentation.postdetail

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post

data class PostDetailReplyDraftState(
    val text: String = "",
    val entities: List<MessageEntity> = emptyList(),
    val replyToCommentId: String? = null,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
) {
    val isBlank: Boolean
        get() = text.isBlank()
}

data class PostDetailUiState(
    val post: Post? = null,
    val comments: List<CommentUiModel> = emptyList(),
    val currentUserId: String? = null,
    val isCommentsLoading: Boolean = false,
    val isCommentsError: Boolean = false,
    val isRefreshing: Boolean = false,
    val replyDraft: PostDetailReplyDraftState = PostDetailReplyDraftState(),
    val editingCommentId: String? = null,
    val isSavingCommentEdit: Boolean = false,
) {
    val canSendReply: Boolean
        get() = !replyDraft.isBlank
}

sealed interface PostDetailEvent {
    data object BackTapped : PostDetailEvent
    data object RefreshRequested : PostDetailEvent
    data object PostUpvoteTapped : PostDetailEvent
    data object PostDownvoteTapped : PostDetailEvent
    data class PollOptionTapped(val optionId: String) : PostDetailEvent
    data object SendReplyTapped : PostDetailEvent
    data class ReplyDraftChanged(
        val text: String,
        val entities: List<MessageEntity>,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) : PostDetailEvent
    data class ReplySelectionChanged(val start: Int, val end: Int) : PostDetailEvent
    data class CommentReplyTapped(val comment: Comment) : PostDetailEvent
    data object CancelReplyTapped : PostDetailEvent
    data class CommentUpvoteTapped(val commentId: String) : PostDetailEvent
    data class CommentDownvoteTapped(val commentId: String) : PostDetailEvent
    data class CommentDeleteTapped(val commentId: String) : PostDetailEvent
    data class CommentMenuTapped(val comment: Comment) : PostDetailEvent
    data class PostMenuTapped(val post: Post) : PostDetailEvent
    data class AuthorTapped(val userId: String) : PostDetailEvent
}
