package com.axoo.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/** Velo progressivo dietro i controlli inferiori, senza intercettare i tocchi. */
internal class BottomFadeView(
    context: Context,
    color: Int = NativeColors.surface,
) : View(context) {
    val fadeHeightPx = context.dp(72f).toInt()

    init {
        isClickable = false
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00, 0x60, 0xB0, 0xE8).map { alpha ->
                ColorUtils.setAlphaComponent(color, alpha)
            }.toIntArray(),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(fadeHeightPx, heightMeasureSpec),
        )
    }
}
