package com.axoo.features.auth.presentation

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.axoo.ui.components.setRoundedBackground
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

/** Shared status screen for session recovery and logout. */
internal class AuthStatusView(
    context: Context,
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(48), context.dp(24), context.dp(48))
        setBackgroundColor(NativeColors.background)
        addView(TextView(context).apply {
            text = title
            textSize = 22f
            typeface = context.baloo2Typeface(700)
            includeFontPadding = false
            setTextColor(NativeColors.textPrimary)
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (message != null) {
            addView(TextView(context).apply {
                text = message
                textSize = 14f
                typeface = context.interTypeface(400)
                includeFontPadding = false
                setLineSpacing(context.dp(3).toFloat(), 1f)
                setTextColor(NativeColors.textSecondary)
                gravity = Gravity.CENTER
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(12)
                bottomMargin = context.dp(24)
            })
        }
        if (onRetry != null) {
            addView(TextView(context).apply {
                text = "Riprova"
                textSize = 18f
                typeface = context.baloo2Typeface(700)
                gravity = Gravity.CENTER
                setTextColor(NativeColors.onAccent)
                setRoundedBackground(NativeColors.accent, context.dp(18).toFloat())
                isFocusable = true
                setOnClickListener {
                    isEnabled = false
                    onRetry()
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(58)))
        }
        if (onLogout != null) {
            addView(TextView(context).apply {
                text = "Esci"
                textSize = 18f
                typeface = context.baloo2Typeface(700)
                gravity = Gravity.CENTER
                setTextColor(NativeColors.accent)
                isFocusable = true
                setOnClickListener { onLogout() }
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(58)))
        }
    }
}
