package com.axoo.features.posts.presentation.components

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Disegna un intero con scorrimento verticale: i valori crescenti entrano
 * dal basso, quelli decrescenti dall'alto. Il ritaglio segue l'altezza del font.
 */
internal fun drawRollingNumber(
    canvas: Canvas,
    paint: Paint,
    oldValue: Int,
    currentValue: Int,
    progress: Float,
    left: Float,
    right: Float,
    centerY: Float,
    halfHeightPx: Float,
    gapPx: Float,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    val halfHeight = maxOf(halfHeightPx, lineHeight / 2f)
    val top = centerY - halfHeight
    val bottom = centerY + halfHeight
    val baseY = top + (bottom - top - lineHeight) * 0.5f - metrics.ascent
    val oldText = oldValue.toString()
    val currentText = currentValue.toString()
    val oldX = (left + right - paint.measureText(oldText)) * 0.5f
    val currentX = (left + right - paint.measureText(currentText)) * 0.5f

    val save = canvas.save()
    canvas.clipRect(left, top, right, bottom)
    if (oldValue == currentValue || safeProgress >= 1f) {
        canvas.drawText(currentText, currentX, baseY, paint)
    } else {
        val distance = lineHeight + gapPx
        val direction = if (currentValue > oldValue) -1f else 1f
        canvas.drawText(
            oldText,
            oldX,
            baseY + direction * distance * safeProgress,
            paint,
        )
        canvas.drawText(
            currentText,
            currentX,
            baseY - direction * distance + direction * distance * safeProgress,
            paint,
        )
    }
    canvas.restoreToCount(save)
}

/** Same reel effect as [drawRollingNumber], applied to one OTP digit. */
