package com.axoo.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.axoo.ui.theme.AppFontFamily
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.applyAxooTypography
import com.axoo.ui.theme.dp

internal class UnreadCountBadgeRenderer(context: Context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.dp(11f)
        color = NativeColors.onDestructive
        applyAxooTypography(context, AppFontFamily.BALOO2, 800)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.destructive
    }
    private val horizontalPaddingPx = context.dp(5f)

    val heightPx: Float = context.dp(16f)

    fun widthPx(count: Int): Float =
        textPaint.measureText(unreadCountBadgeText(count)) + horizontalPaddingPx * 2f

    fun draw(canvas: Canvas, count: Int, left: Float, top: Float) {
        if (count <= 0) return
        val width = widthPx(count)
        val bounds = RectF(left, top, left + width, top + heightPx)
        canvas.drawRoundRect(bounds, heightPx / 2f, heightPx / 2f, backgroundPaint)
        val baseline = top + heightPx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(unreadCountBadgeText(count), bounds.centerX(), baseline, textPaint)
    }
}

internal fun unreadCountBadgeText(count: Int): String =
    if (count > 99) "99+" else count.coerceAtLeast(0).toString()
