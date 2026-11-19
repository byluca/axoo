package com.axoo.application.navigation.factory

import android.view.View
import com.axoo.features.posts.presentation.sheets.PostOptionsSheet
import com.axoo.features.moderation.presentation.showModeratorHideConfirmation
import com.axoo.features.home.presentation.FeedViewModel
import com.axoo.features.moderation.presentation.ModerationViewModel
import com.axoo.features.posts.domain.Post
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.features.posts.presentation.report.ReportSheet

/** Azioni sui post del feed, incluse le relative conferme e gli errori. */
internal class FeedPostMenu(
    private val host: View,
    private val feedVm: FeedViewModel,
    private val profileVm: ProfileViewModel,
    private val moderationVm: ModerationViewModel,
    private val onEdit: (Post) -> Unit,
) {
    fun show(post: Post, isOwn: Boolean) {
        PostOptionsSheet.present(
            anchor = host,
            isOwnPost = isOwn,
            canModerate = !isOwn && (profileVm.me.value.getOrNull()?.isModerator == true),
            canEdit = post.poll == null,
            onReport = {
                lateinit var report: ReportSheet.Handle
                report = ReportSheet.present(
                    anchor = host,
                    onSubmit = { reason ->
                        feedVm.reportPost(
                            postId = post.id,
                            reason = reason.apiValue,
                            onSuccess = report::complete,
                            onFailure = { _ ->
                                report.showError(
                                    OverlayCopy.reportError,
                                )
                            },
                        )
                    },
                )
            },
            onHide = { feedVm.hidePost(post.id) { OverlayCard.error(host, OverlayCopy.operationError) } },
            onBlock = {
                showBlockUserConfirmation(host) {
                    feedVm.blockUser(post.userId) { result ->
                        result.exceptionOrNull()?.let { OverlayCard.error(host, OverlayCopy.operationError) }
                    }
                }
            },
            onEdit = {
                if (post.poll == null) onEdit(post)
            },
            onDelete = {
                showDeletePostConfirmation(host) {
                    feedVm.deletePost(post.id) { result ->
                        result.fold(
                            onSuccess = {},
                            onFailure = { error ->
                                OverlayCard.error(host, OverlayCopy.deleteError)
                            },
                        )
                    }
                }
            },
            onModerationRemove = {
                showModeratorHideConfirmation(
                    anchor = host,
                    moderationViewModel = moderationVm,
                    targetType = "post",
                    targetId = post.id,
                    onSuccess = {
                        feedVm.removePostFromFeeds(post.id)
                    },
                )
            },
        )
    }
}

private fun showDeletePostConfirmation(anchor: View, onConfirm: () -> Unit) {
    OverlayCard.present(
        anchor = anchor,
        title = OverlayCopy.deletePostTitle,
        message = OverlayCopy.deletePostMessage,
        primaryAction = OverlayCardAction(
            text = OverlayCopy.delete,
            destructive = true,
            onClick = onConfirm,
        ),
        secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
    )
}

private fun showBlockUserConfirmation(anchor: View, onConfirm: () -> Unit) {
    OverlayCard.present(
        anchor = anchor,
        title = OverlayCopy.blockTitle,
        message = OverlayCopy.blockMessage,
        primaryAction = OverlayCardAction(text = OverlayCopy.block, destructive = true, onClick = onConfirm),
        secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
    )
}
