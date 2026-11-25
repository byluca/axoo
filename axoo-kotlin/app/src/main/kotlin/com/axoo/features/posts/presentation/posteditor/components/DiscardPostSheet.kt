package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.axoo.ui.sheets.BottomSheetConfig
import com.axoo.ui.sheets.BottomSheetContainer
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/**
 * Material bottom sheet that confirms discarding an unsaved post.
 * The handle lets the editor dismiss the dialog during cleanup.
 */
object DiscardPostSheet {

    class Handle internal constructor(private val view: NativeDiscardPostSheetView) {
        fun dismiss() {
            view.dismiss()
        }
    }

    fun present(
        anchor: View,
        isEditing: Boolean,
        onConfirmDiscard: () -> Unit,
        onContinueWriting: () -> Unit,
        onDismissed: () -> Unit = {},
    ): Handle {
        val sheet = NativeDiscardPostSheetView(
            context = anchor.context,
            isEditing = isEditing,
            onConfirmDiscard = onConfirmDiscard,
            onContinueWriting = onContinueWriting,
            onDismissed = onDismissed,
        )
        sheet.presentOn(anchor)
        return Handle(sheet)
    }
}

@SuppressLint("ViewConstructor")
internal class NativeDiscardPostSheetView(
    context: Context,
    private val isEditing: Boolean,
    private val onConfirmDiscard: () -> Unit,
    private val onContinueWriting: () -> Unit,
    private val onDismissed: () -> Unit,
) : BottomSheetContainer(
    context = context,
    config = BottomSheetConfig(
        sheetBackgroundColor = NativeColors.background,
        dismissOnScrimClick = true,
        dragToDismiss = true,
    ),
) {
    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setSheetContent(createContent())
        addOnDismissedListener { onDismissed() }
    }

    private fun createContent(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))

            addView(makeTitle(OverlayCopy.discardTitle))
            addView(makeBody(
                if (isEditing) OverlayCopy.discardEditMessage
                else OverlayCopy.discardCreateMessage,
            ).apply {
                setPadding(0, dp(6), 0, dp(12))
            })

            addView(makeActionText(if (isEditing) OverlayCopy.discardEdit else OverlayCopy.discardCreate, destructive = true).apply {
                setOnClickListener {
                    dismissThen(onConfirmDiscard)
                }
            })

            addView(makeActionText(OverlayCopy.continueWriting).apply {
                setOnClickListener {
                    dismissThen(onContinueWriting)
                }
            })

            setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
