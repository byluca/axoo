/** Piccole estensioni riutilizzabili per le View Android. */
package com.axoo.ui.components

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.annotation.ColorInt

/** Shared reference scale for post cards, comments and the navigation pill. */
internal fun Context.cardPixelsPerPoint(width: Int = resources.displayMetrics.widthPixels): Float =
    minOf(resources.displayMetrics.density, width / 414f)

internal fun View.setCardShadow(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (view.width == 0 || view.height == 0) {
                outline.setEmpty()
            } else {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    }
    elevation = 0f
    translationZ = 0f
    clipToOutline = false
}

internal fun View.setControlShadow(radiusPx: Float) {
    setCardShadow(radiusPx)
    elevation = context.resources.displayMetrics.density * NativePillDefaults.controlElevationDp
}

fun View.setRoundedBackground(
    @ColorInt color: Int,
    radiusPx: Float,
) {
    background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }
}

inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParamsTyped(
    block: T.() -> Unit,
) {
    val params = layoutParams
    if (params is T) {
        params.block()
        layoutParams = params
    }
}
