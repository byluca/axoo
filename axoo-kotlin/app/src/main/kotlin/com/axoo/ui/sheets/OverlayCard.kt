package com.axoo.ui.sheets

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.axoo.R
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class OverlayCardAction(
    val text: String,
    val destructive: Boolean = false,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit,
)

/** Card compatta condivisa; Material gestisce la finestra e la chiusura del dialogo. */
object OverlayCard {
    class Handle internal constructor(
        private val dismissAction: () -> Unit,
        private val busyAction: (Boolean) -> Unit,
    ) {
        fun dismiss() = dismissAction()
        fun setBusy(busy: Boolean) = busyAction(busy)
    }

    fun error(anchor: View, message: String): Handle = present(
        anchor = anchor,
        title = OverlayCopy.errorTitle,
        message = message,
        primaryAction = OverlayCardAction(OverlayCopy.ok) {},
    )

    fun present(
        anchor: View,
        title: String?,
        message: String,
        primaryAction: OverlayCardAction,
        secondaryAction: OverlayCardAction? = null,
        contentView: View? = null,
        headerView: View? = null,
        lineSpacingDp: Float = 2f,
        dismissOnBack: Boolean = true,
        onDismissed: () -> Unit = {},
    ): Handle {
        val context = ContextThemeWrapper(anchor.context, R.style.Theme_Axoo_OverlayCard)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        headerView?.let { content.addView(it) }
        if (title != null) {
            content.addView(TextView(context).apply {
                text = title
                if (headerView != null) setPadding(0, dp(12), 0, 0)
                applySheetTypography(BottomSheetTypography.confirmationTitle)
                setTextColor(NativeColors.textPrimary)
            })
        }
        content.addView(TextView(context).apply {
            text = message
            applySheetTypography(BottomSheetTypography.body)
            setTextColor(NativeColors.textSecondary)
            setLineSpacing(dp(lineSpacingDp), 1f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (title != null) topMargin = context.dp(8)
        })
        contentView?.let {
            content.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = context.dp(8) })
        }
        val actions = ActionRow(context)
        content.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = context.dp(16) })
        val scroll = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            isFillViewport = false
            addView(content)
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setView(scroll)
            .setBackgroundInsetStart(0)
            .setBackgroundInsetEnd(0)
            .setBackgroundInsetTop(0)
            .setBackgroundInsetBottom(0)
            .setCancelable(dismissOnBack)
            .setOnDismissListener { onDismissed() }
        val dialog = builder.create()
        secondaryAction?.let { actions.addView(actionButton(context, dialog, it, secondary = true)) }
        actions.addView(actionButton(context, dialog, primaryAction, secondary = false))
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        val availableWidth = anchor.rootView.width.takeIf { it > 0 } ?: anchor.resources.displayMetrics.widthPixels
        dialog.window?.setLayout(minOf(context.dp(320), (availableWidth - context.dp(48)).coerceAtLeast(1)), ViewGroup.LayoutParams.WRAP_CONTENT)
        return Handle(
            dismissAction = { dialog.dismiss() },
            busyAction = { busy ->
                dialog.setCancelable(dismissOnBack && !busy)
                for (index in 0 until actions.childCount) actions.getChildAt(index).isEnabled = !busy
            },
        )
    }

    private fun actionButton(context: Context, dialog: AlertDialog, action: OverlayCardAction, secondary: Boolean) =
        MaterialButton(context, null, androidx.appcompat.R.attr.buttonBarPositiveButtonStyle).apply {
            text = action.text
            applySheetTypography(BottomSheetTypography.confirmationAction)
            isAllCaps = false
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
            letterSpacing = 0f
            insetTop = 0
            insetBottom = 0
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            minimumWidth = 0
            minWidth = 0
            minimumHeight = context.dp(44)
            minHeight = context.dp(44)
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            setTextColor(
                when {
                    action.destructive -> NativeColors.destructive
                    secondary -> NativeColors.textSecondary
                    else -> NativeColors.textPrimary
                },
            )
            setOnClickListener {
                if (!dialog.isShowing || !isEnabled) return@setOnClickListener
                // Navigation actions close first; forms can keep the card open
                // while validating or waiting for the server.
                if (action.dismissOnClick) dialog.dismiss()
                action.onClick()
            }
        }

    private class ActionRow(context: Context) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val gap = context.dp(8)
            val buttonWidth = (availableWidth - gap) / 2
            val stacked = childCount > 1 && (0 until childCount).any {
                val button = getChildAt(it) as TextView
                button.paint.measureText(button.text.toString()) + button.paddingLeft + button.paddingRight > buttonWidth
            }
            orientation = if (stacked) VERTICAL else HORIZONTAL
            for (index in 0 until childCount) {
                (getChildAt(index).layoutParams as LayoutParams).apply {
                    width = if (stacked) LayoutParams.MATCH_PARENT else 0
                    height = LayoutParams.WRAP_CONTENT
                    weight = if (stacked) 0f else 1f
                    topMargin = if (stacked && index > 0) gap else 0
                    marginStart = if (!stacked && index > 0) gap else 0
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
