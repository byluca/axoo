package com.axoo.features.moderation.presentation

import android.view.View
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy

internal fun showModeratorHideConfirmation(
    anchor: View,
    moderationViewModel: ModerationViewModel,
    targetType: String,
    targetId: String,
    onSuccess: () -> Unit,
) {
    OverlayCard.present(
        anchor = anchor,
        title = OverlayCopy.moderationConfirmTitle,
        message = OverlayCopy.moderationConfirmMessage,
        primaryAction = OverlayCardAction(
            text = OverlayCopy.confirm,
            destructive = true,
            onClick = {
                moderationViewModel.act(
                    action = "hide",
                    targetType = targetType,
                    targetId = targetId,
                    reason = "other",
                    onSuccess = onSuccess,
                    onFailure = { OverlayCard.error(anchor, OverlayCopy.moderationError) },
                )
            },
        ),
        secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
    )
}
