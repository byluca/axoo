package com.axoo.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.EditText
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

/**
 * Componente condiviso ufficiale per i campi di testo.
 * Riprende l'estetica delle "pillole" (NativePillCard / Comment Composer).
 */
open class AxooTextInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    init {
        // Colori e Tipografia
        setTextColor(NativeColors.textPrimary)
        setHintTextColor(NativeColors.textSecondary)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = context.interTypeface(400) // Applica il font ufficiale Inter

        // Genera lo sfondo stile "Pillola"
        val cornerRadiusPx = context.dp(16f) // Uguale ai bottoni e al composer
        val borderWidthPx = context.dp(1f).toInt().coerceAtLeast(1)

        background = GradientDrawable().apply {
            setColor(NativeColors.surface)
            cornerRadius = cornerRadiusPx
            setStroke(borderWidthPx, NativeColors.border)
        }

        // Padding interno comodo
        val horizontalPadding = context.dp(16f).toInt()
        val verticalPadding = context.dp(14f).toInt()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
    }
}
