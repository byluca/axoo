package com.axoo.features.posts.presentation.postdetail.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativePostDetailTopBarView(
    context: Context,
) : FrameLayout(context) {

    private var onBack: () -> Unit = {}
    private var statusTopInset = 0

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private val backButton = ArrowBackButton(context, iconSizeDp = 24)
    private val title = TextView(context).apply {
        text = "Modifica commento"
        textSize = 20f
        typeface = context.baloo2Typeface(800)
        setTextColor(NativeColors.textPrimary)
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }

    init {
        setBackgroundColor(NativeColors.card)
        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        row.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(
            title,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) },
        )

        addView(
            View(context).apply { setBackgroundColor(NativeColors.divider) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(1), Gravity.BOTTOM),
        )

        backButton.setOnClickListener {
            onBack()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            applyInsets()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setOnBack(callback: () -> Unit) {
        onBack = callback
    }

    fun bind(editingComment: Boolean) {
        title.visibility = if (editingComment) VISIBLE else GONE
    }

    private fun applyInsets() {
        row.setPadding(dp(4), statusTopInset + dp(4), dp(4), dp(4))
        requestLayout()
    }
}
