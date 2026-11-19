package com.axoo.features.posts.presentation.postdetail.components

import android.view.View
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCopy

/**
 * Native Comment options bottom sheet. Native-only.
 */
object CommentOptionsSheet {

    class Handle internal constructor(private val inner: OptionsSheet.Handle) {
        fun dismiss() = inner.dismiss()
    }

    fun present(
        anchor: View,
        isOwnComment: Boolean,
        canModerate: Boolean = false,
        canEdit: Boolean = true,
        onReport: () -> Unit,
        onBlock: () -> Unit = {},
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onModerationRemove: () -> Unit = {},
        onDismissed: () -> Unit = {},
    ): Handle {
        val actions = buildList {
            if (!isOwnComment) {
                add(OptionsSheetAction(OverlayCopy.reportComment, "flag.svg") { onReport() })
                add(OptionsSheetAction(OverlayCopy.blockUser, "close-square.svg", destructive = true) { onBlock() })
                if (canModerate) {
                    add(
                        OptionsSheetAction(
                            text = OverlayCopy.hide,
                            iconAsset = "delete.svg",
                            destructive = true,
                        ) { onModerationRemove() },
                    )
                }
            }
            if (isOwnComment) {
                if (canEdit) {
                    add(OptionsSheetAction(OverlayCopy.editComment, "pencil.svg") { onEdit() })
                }
                add(
                    OptionsSheetAction(
                        text = OverlayCopy.deleteComment,
                        iconAsset = "delete.svg",
                        destructive = true,
                    ) { onDelete() },
                )
            }
        }
        return Handle(
            OptionsSheet.present(
                anchor = anchor,
                actions = actions,
                onDismissed = onDismissed,
            ),
        )
    }
}
