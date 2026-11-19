package com.axoo.features.auth.presentation

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

internal class ComingSoonView(context: Context) : FrameLayout(context) {

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
                    id = "money-locked",
                    path = "axooji/ui/Money.tgs",
                )
            },
            LinearLayout.LayoutParams(context.dp(230), context.dp(230)).apply {
                bottomMargin = context.dp(18)
            },
        )
        
        val title = TextView(context).apply {
            text = "ABBIAMO FINITO I SOLDI."
            textSize = 28f
            typeface = context.baloo2Typeface(800)
            setTextColor(NativeColors.accent)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        
        center.addView(
            title,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(16)
            }
        )
        
        val subtitleText = "<i>(Scherziamo, i server per ora riusciamo ancora a pagarli).</i><br><br>" +
            "Il vero problema è che stiamo finendo l'app per iOS. Lanciare Axoo ora, tagliando fuori i tuoi compagni con l'iPhone, non avrebbe senso.<br><br>" +
            "Vogliamo aprire le porte a <b>tutto l'Ateneo</b> nello stesso identico momento.<br><br>" +
            "Tieni l'app installata: apriamo i cancelli tra circa una settimana. <b>Scalda i pollici!</b>"
            
        val subtitle = TextView(context).apply {
            text = HtmlCompat.fromHtml(subtitleText, HtmlCompat.FROM_HTML_MODE_COMPACT)
            textSize = 16f
            typeface = context.interTypeface(400)
            setTextColor(NativeColors.textSecondary)
            gravity = Gravity.CENTER
            setLineSpacing(context.dp(4).toFloat(), 1f)
            includeFontPadding = false
        }
        
        center.addView(
            subtitle,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(8)
                marginEnd = context.dp(8)
            }
        )
        
        content.addView(center, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }
}
