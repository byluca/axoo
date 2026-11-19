package com.axoo.engine.emoji.text

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Segnaposto invisibile per le emoji animate (Lottie).
 * Siccome Android non supporta le Lottie nel testo, questa classe inganna il sistema:
 *
 * 1. In `getSize` chiede ad Android di fare un "buco" largo `renderSizePx` e
 *    modifica `fm` per forzare l'altezza della riga e allinearla perfettamente.
 * 2. In `draw` NON disegna niente sul Canvas. Si limita a calcolare il punto
 *    centrale (X, Y) del buco e lo salva in `lastDrawnCx` e `lastDrawnCy`.
 *
 * Successivamente, l'`AnimatedEmojiRenderer` leggerà queste coordinate e
 * andrà a stampare a video l'animazione Lottie esattamente in quel punto.
 */
class AnimatedEmojiSpan(
    val renderSizePx: Int,
    private val fontMetrics: Paint.FontMetricsInt? = null,
    val emojiIndex: Int = -1,
    val emojiName: String? = null,
    val fallbackChar: String = "",
) : ReplacementSpan() {

    private val measuredSizePx = renderSizePx.coerceAtLeast(1)

    var lastDrawnCx = 0f
    var lastDrawnCy = 0f
    var spanDrawn = false

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val sourceMetrics = fontMetrics ?: paint.fontMetricsInt
            fm.ascent = sourceMetrics.ascent
            fm.descent = sourceMetrics.descent
            fm.top = sourceMetrics.top
            fm.bottom = sourceMetrics.bottom
            fm.leading = sourceMetrics.leading
        }
        return measuredSizePx
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        lastDrawnCx = x + measuredSizePx / 2f
        lastDrawnCy = top + (bottom - top) / 2f
        spanDrawn = true
    }
}
