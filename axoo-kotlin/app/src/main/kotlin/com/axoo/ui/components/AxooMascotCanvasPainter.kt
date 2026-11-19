package com.axoo.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.axoo.ui.theme.NativeColors
import kotlin.math.cos

/**
 * Code-native rendering of the Axoo mascot from assets/images/new-logo-777.png.
 *
 * Coordinates are normalized to a square, so the same drawing remains sharp at notification,
 * refresh-indicator, and larger decorative sizes without loading a bitmap.
 */
internal class AxooMascotCanvasPainter {
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.white
        style = Paint.Style.FILL
    }
    private val gillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.white
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111014.toInt()
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bodyPath = Path()
    private val mouthPath = Path()

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        gillPhaseDegrees: Float? = null,
        squash: Float = 0f,
    ) {
        val size = minOf(bounds.width(), bounds.height())
        if (size <= 0f) return
        val left = bounds.centerX() - size / 2f
        val top = bounds.centerY() - size / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size, size)

        drawGills(canvas, gillPhaseDegrees)

        val faceScaleX = 1f - squash * 0.035f
        val faceScaleY = 1f + squash * 0.055f
        canvas.save()
        canvas.scale(faceScaleX, faceScaleY, CENTER_X, CENTER_Y)
        drawHead(canvas)
        drawFeatures(canvas)
        canvas.restore()

        canvas.restore()
    }

    private fun drawGills(canvas: Canvas, phaseDegrees: Float?) {
        // Keep every gill physically attached to the head as in new-logo-777.png. The loading motion
        // travels through opacity and thickness instead of rotating the geometry around the face.
        drawGill(canvas, phaseDegrees, 0, 0.365f, 0.345f, 0.255f, 0.165f)
        drawGill(canvas, phaseDegrees, 1, 0.635f, 0.345f, 0.745f, 0.165f)
        drawGill(canvas, phaseDegrees, 2, 0.765f, 0.460f, 0.860f, 0.410f)
        drawGill(canvas, phaseDegrees, 3, 0.795f, 0.590f, 0.865f, 0.600f)
        drawGill(canvas, phaseDegrees, 4, 0.205f, 0.590f, 0.135f, 0.600f)
        drawGill(canvas, phaseDegrees, 5, 0.235f, 0.460f, 0.140f, 0.410f)
    }

    private fun drawGill(
        canvas: Canvas,
        phaseDegrees: Float?,
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ) {
        val pulse = phaseDegrees?.let {
            val phase = it / 360f
            ((cos((index / GILL_COUNT.toFloat() - phase) * FULL_TURN_RADIANS) + 1f) / 2f)
        } ?: 1f
        gillPaint.alpha = (GILL_MIN_ALPHA + pulse * (255 - GILL_MIN_ALPHA)).toInt()
        gillPaint.strokeWidth = GILL_BASE_WIDTH + pulse * GILL_PULSE_WIDTH
        canvas.drawLine(startX, startY, endX, endY, gillPaint)
    }

    private fun drawHead(canvas: Canvas) {
        bodyPath.reset()
        bodyPath.moveTo(0.500f, 0.242f)
        bodyPath.cubicTo(0.670f, 0.242f, 0.775f, 0.315f, 0.805f, 0.455f)
        bodyPath.cubicTo(0.832f, 0.575f, 0.810f, 0.690f, 0.735f, 0.760f)
        bodyPath.cubicTo(0.670f, 0.815f, 0.585f, 0.825f, 0.500f, 0.825f)
        bodyPath.cubicTo(0.415f, 0.825f, 0.330f, 0.815f, 0.265f, 0.760f)
        bodyPath.cubicTo(0.190f, 0.690f, 0.168f, 0.575f, 0.195f, 0.455f)
        bodyPath.cubicTo(0.225f, 0.315f, 0.330f, 0.242f, 0.500f, 0.242f)
        bodyPath.close()
        canvas.drawPath(bodyPath, whitePaint)
    }

    private fun drawFeatures(canvas: Canvas) {
        canvas.drawRoundRect(0.305f, 0.432f, 0.415f, 0.618f, 0.055f, 0.055f, featurePaint)
        canvas.drawRoundRect(0.585f, 0.432f, 0.695f, 0.618f, 0.055f, 0.055f, featurePaint)

        drawEyeHighlight(canvas, centerX = 0.345f, centerY = 0.478f)
        drawEyeHighlight(canvas, centerX = 0.625f, centerY = 0.478f)

        mouthPath.reset()
        mouthPath.moveTo(0.390f, 0.660f)
        mouthPath.cubicTo(0.445f, 0.725f, 0.555f, 0.725f, 0.610f, 0.660f)
        featurePaint.style = Paint.Style.STROKE
        featurePaint.strokeWidth = 0.045f
        canvas.drawPath(mouthPath, featurePaint)
        featurePaint.style = Paint.Style.FILL
    }

    private fun drawEyeHighlight(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.save()
        canvas.rotate(20f, centerX, centerY)
        canvas.drawOval(
            centerX - 0.017f,
            centerY - 0.030f,
            centerX + 0.017f,
            centerY + 0.030f,
            whitePaint,
        )
        canvas.restore()
    }

    private companion object {
        const val CENTER_X = 0.5f
        const val CENTER_Y = 0.53f
        const val GILL_COUNT = 6
        const val GILL_MIN_ALPHA = 158
        const val GILL_BASE_WIDTH = 0.118f
        const val GILL_PULSE_WIDTH = 0.014f
        const val FULL_TURN_RADIANS = (Math.PI * 2.0).toFloat()
    }
}
