package com.axoo.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.axoo.R
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

/** Material controls; the editor uses UIKit's flat surface and spinner-only saving state. */
internal class NativeLoadingButton(
    context: Context,
    private val primary: Boolean = true,
    textSizeSp: Float = 16f,
    fontWeight: Int = 700,
    radiusDp: Int = 16,
    private val horizontalInsetDp: Int = 16,
    verticalInsetDp: Int = 0,
    wrapsText: Boolean = false,
    private val appearance: Appearance = Appearance.DEFAULT,
) : FrameLayout(context) {
    enum class Appearance { DEFAULT, POST_EDITOR }

    private val editorAppearance = appearance == Appearance.POST_EDITOR
    private val indicatorSizeDp = if (editorAppearance) 20 else 18
    private val button = MaterialButton(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)).apply {
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        if (editorAppearance) {
            elevation = 0f
            stateListAnimator = null
            strokeWidth = 0
        }
        cornerRadius = dp(radiusDp)
        textSize = textSizeSp
        typeface = context.baloo2Typeface(fontWeight)
        includeFontPadding = false
        letterSpacing = 0f
        isAllCaps = false
        if (wrapsText) {
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }
        setPadding(dp(horizontalInsetDp), dp(verticalInsetDp), dp(horizontalInsetDp), dp(verticalInsetDp))
        setOnClickListener { this@NativeLoadingButton.performClick() }
    }
    private val activity = buttonActivityIndicator(context, if (primary) NativeColors.onAccent else NativeColors.accent, indicatorSizeDp)

    init {
        isEnabled = false
        button.isEnabled = false
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(activity, LayoutParams(dp(indicatorSizeDp), dp(indicatorSizeDp), Gravity.CENTER))
    }

    fun bind(title: String, enabled: Boolean, loading: Boolean) {
        isEnabled = enabled && !loading
        button.isEnabled = isEnabled
        button.text = if (editorAppearance && loading) "" else title
        val minimumButtonWidth = if (editorAppearance && loading) dp(horizontalInsetDp * 2 + indicatorSizeDp) else 0
        button.minWidth = minimumButtonWidth
        button.minimumWidth = minimumButtonWidth
        button.setTextColor(when {
            loading -> Color.TRANSPARENT
            !primary -> NativeColors.accent
            isEnabled -> NativeColors.onAccent
            else -> NativeColors.textSecondary
        })
        button.backgroundTintList = ColorStateList.valueOf(when {
            !primary -> Color.TRANSPARENT
            isEnabled || (loading && !editorAppearance) -> NativeColors.accent
            else -> NativeColors.surface
        })
        alpha = if (!primary && !isEnabled && !loading) 0.55f else 1f
        activity.setIndicatorColor(if (editorAppearance) NativeColors.textSecondary
            else if (primary) NativeColors.onAccent else NativeColors.accent)
        activity.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun performClick(): Boolean = isEnabled && super.performClick()
}

internal fun buttonActivityIndicator(context: Context, color: Int = NativeColors.onAccent, sizeDp: Int = 18) =
    CircularProgressIndicator(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)).apply {
        isIndeterminate = true
        trackThickness = dp(2)
        indicatorSize = dp(sizeDp)
        indicatorInset = 0
        trackColor = Color.TRANSPARENT
        setIndicatorColor(color)
        visibility = View.GONE
    }
