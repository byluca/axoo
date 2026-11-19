package com.axoo.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import coil.load
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/** Pulsante Back condiviso dalle top bar native. */
internal class ArrowBackButton(context: Context, private val iconSizeDp: Int = 24) : ImageView(context) {

    init {
        isClickable = true
        isFocusable = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        imageTintList = ColorStateList.valueOf(NativeColors.textPrimary)
        val padding = context.dp((44 - iconSizeDp) / 2)
        setPadding(padding, padding, padding, padding)
        load("file:///android_asset/icons/arrow-left.svg")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val iconSize = context.dp(iconSizeDp)
        val horizontal = ((w - iconSize) / 2).coerceAtLeast(0)
        val vertical = ((h - iconSize) / 2).coerceAtLeast(0)
        setPadding(horizontal, vertical, horizontal, vertical)
    }
}
