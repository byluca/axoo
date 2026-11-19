package com.axoo.features.auth.presentation

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.ui.components.setRoundedBackground
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

internal class AccountRestrictionView(
    context: Context,
    title: String,
    message: String,
    onOpenTerms: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) : FrameLayout(context) {

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dp(22), context.dp(12), context.dp(22), context.dp(20))
    }

    init {
        setBackgroundColor(NativeColors.background)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            content.setPadding(
                context.dp(22),
                status.top + context.dp(10),
                context.dp(22),
                navigation.bottom + context.dp(20),
            )
            insets
        }

        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, context.dp(12), 0, 0)
        }
        center.addView(
            AnimatedLottieView(context, context.dp(230)).apply {
                setAnimation(
                    id = "account-restriction",
                    path = NO_ENTRY_ASSET,
                )
            },
            LinearLayout.LayoutParams(context.dp(230), context.dp(230)).apply {
                bottomMargin = context.dp(18)
            },
        )
        center.addView(
            label(title, 30f, 700, NativeColors.textPrimary).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                letterSpacing = -0.02f
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            },
        )
        center.addView(
            label(message, 16f, 400, NativeColors.textSecondary).apply {
                gravity = Gravity.CENTER
                setLineSpacing(context.dp(3).toFloat(), 1f)
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(12)
                marginEnd = context.dp(12)
            },
        )
        content.addView(center, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val terms = label("Termini di servizio", 14f, 700, NativeColors.accent).apply {
            gravity = Gravity.CENTER
            minimumHeight = context.dp(44)
            setOnClickListener { onOpenTerms() }
        }
        content.addView(terms, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(label("Elimina account", 14f, 700, NativeColors.textSecondary).apply {
            gravity = Gravity.CENTER
            minimumHeight = context.dp(44)
            setOnClickListener { onDeleteAccount() }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(Space(context), LinearLayout.LayoutParams(1, context.dp(6)))

        content.addView(
            TextView(context).apply {
                text = "Esci dall'account"
                textSize = 18f
                typeface = context.baloo2Typeface(700)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(NativeColors.onAccent)
                setRoundedBackground(NativeColors.accent, context.dp(18).toFloat())
                clipToOutline = true
                isClickable = true
                isFocusable = true
                setOnClickListener { onLogout() }
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(58)),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Questa schermata viene montata dopo che l'Activity è già edge-to-edge:
        // richiediamo gli inset correnti per non finire sotto status/navigation bar.
        ViewCompat.requestApplyInsets(this)
    }

    private fun label(value: String, size: Float, weight: Int, color: Int) = TextView(context).apply {
        text = value
        textSize = size
        typeface = context.baloo2Typeface(weight)
        setTextColor(color)
        includeFontPadding = false
    }

    private companion object {
        const val NO_ENTRY_ASSET = "axooji/ui/no-entry.tgs"
    }
}
