package com.axoo.features.posts.presentation.sheets

import android.view.View
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCopy

/**
 * Native Post options bottom sheet. Native-only.
 */
object PostOptionsSheet {

    class Handle internal constructor(private val inner: OptionsSheet.Handle) {
        fun dismiss() = inner.dismiss()
    }

    fun present(
        anchor: View,
        isOwnPost: Boolean,
        canModerate: Boolean = false,
        canEdit: Boolean = true,
        onReport: () -> Unit,
        onBlock: () -> Unit = {},
        onHide: () -> Unit = {},
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onModerationRemove: () -> Unit = {},
        onDismissed: () -> Unit = {},
    ): Handle {
        val actions = buildList {
            if (!isOwnPost) {
                add(OptionsSheetAction("Nascondi per me", "close-square.svg", onClick = onHide))
                add(OptionsSheetAction(OverlayCopy.reportPost, "flag.svg") { onReport() })
                add(OptionsSheetAction(OverlayCopy.blockAuthor, "close-square.svg", destructive = true) { onBlock() })
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
            if (isOwnPost) {
                if (canEdit) {
                    add(OptionsSheetAction(OverlayCopy.editPost, "pencil.svg") { onEdit() })
                }
                add(
                    OptionsSheetAction(
                        text = OverlayCopy.deletePost,
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
