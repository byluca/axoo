package com.axoo.features.posts.presentation.report

import com.axoo.ui.sheets.BottomSheetContainer
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.sheets.BottomSheetConfig
import com.axoo.ui.sheets.BottomSheetTypography
import com.axoo.ui.sheets.applySheetTypography
import com.axoo.features.posts.domain.ReportReason

import android.annotation.SuppressLint
import com.axoo.features.posts.presentation.report.ReportViewModel
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.axoo.core.haptics.Haptics
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

object ReportSheet {
    class Handle internal constructor(private val view: NativeReportSheetView) {
        fun complete() = view.complete()

        fun showError(message: String) = view.showError(message)

        fun dismiss() = view.dismiss()
    }

    fun present(
        anchor: View,
        onSubmit: (ReportReason) -> Unit,
        onDismissed: () -> Unit = {},
    ): Handle {
        val sheet = NativeReportSheetView(
            context = anchor.context,
            onSubmit = onSubmit,
            onDismissed = onDismissed,
        )
        sheet.presentOn(anchor)
        return Handle(sheet)
    }
}

@SuppressLint("ViewConstructor")
internal class NativeReportSheetView(
    context: Context,
    private val onSubmit: (ReportReason) -> Unit,
    onDismissed: () -> Unit,
) : BottomSheetContainer(
    context = context,
    config = BottomSheetConfig(
        sheetBackgroundColor = NativeColors.background,
        dismissOnScrimClick = true,
        dragToDismiss = true,
    ),
) {
    private val viewModel = ReportViewModel()
    private val selectedReason get() = viewModel.selectedReason
    private val submitting get() = viewModel.isSubmitting
    private val reasonRows = linkedMapOf<ReportReason, LinearLayout>()
    private val error = TextView(context).apply {
        visibility = View.GONE
        gravity = Gravity.START
        applySheetTypography(BottomSheetTypography.body)
        setTextColor(NativeColors.destructive)
    }
    private val submit = NativeLoadingButton(context,
        textSizeSp = BottomSheetTypography.action.sizeSp,
        fontWeight = BottomSheetTypography.action.weight, radiusDp = 18,
        verticalInsetDp = 12, wrapsText = true).apply {
        minimumHeight = dp(54)
        setOnClickListener {
            val reason = viewModel.beginSubmit() ?: return@setOnClickListener
            Haptics.confirm(this)
            refresh()
            onSubmit(reason)
        }
    }

    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(20))
        }
        // Match the UIKit stack: 10 between visible children, including an inline error.
        fun append(view: View) {
            content.addView(view, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { if (content.childCount > 0) topMargin = dp(10) })
        }
        append(TextView(context).apply {
            text = OverlayCopy.reportTitle
            applySheetTypography(BottomSheetTypography.title)
            setTextColor(NativeColors.textPrimary)
        })
        append(TextView(context).apply {
            text = OverlayCopy.reportMessage
            applySheetTypography(BottomSheetTypography.body)
            setTextColor(NativeColors.textSecondary)
        })
        ReportReason.entries.forEach { reason ->
            val row = makeReasonRow(reason)
            reasonRows[reason] = row
            append(row)
        }
        append(error)
        append(submit)
        setSheetContent(content)
        addOnDismissedListener(onDismissed)
        refresh()
    }

    fun complete() {
        dismiss()
    }

    fun showError(message: String) {
        viewModel.fail(message)
        refresh()
    }

    private fun makeReasonRow(reason: ReportReason): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (submitting) return@setOnClickListener
            viewModel.select(reason)
            refresh()
        }
        addView(TextView(context).apply {
            text = reason.label
            applySheetTypography(BottomSheetTypography.option)
            setTextColor(NativeColors.textPrimary)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun refresh() {
        allowsDismissal = !submitting
        error.text = viewModel.error
        error.visibility = if (viewModel.error == null) View.GONE else View.VISIBLE
        reasonRows.forEach { (reason, row) ->
            val selected = reason == selectedReason
            row.background = rounded(
                color = if (selected) NativeColors.surface else Color.TRANSPARENT,
                strokeColor = Color.TRANSPARENT,
                strokeWidth = 0,
            )
            row.alpha = if (submitting) 0.65f else 1f
        }
        val enabled = selectedReason != null && !submitting
        submit.bind(OverlayCopy.reportSubmit, enabled, submitting)
    }

    private fun rounded(
        color: Int,
        strokeColor: Int,
        strokeWidth: Int,
        radius: Float = dp(18).toFloat(),
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

}
