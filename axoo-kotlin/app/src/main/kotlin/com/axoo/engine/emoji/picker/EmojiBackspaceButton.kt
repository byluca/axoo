package com.axoo.engine.emoji.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import com.axoo.ui.theme.NativeColors

internal class EmojiBackspaceButton(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.card
        setShadowLayer(4f * density, 0f, 3f * density, 0x33000000)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = NativeColors.cardBorder
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(NativeColors.textPrimary, PorterDuff.Mode.SRC_IN)
    }
    private val iconRect = RectF()
    private var iconBitmap: Bitmap? = null
    private var pressedScale = 1f
    private var discRadius = 0f
    private var discCx = 0f
    private var discCy = 0f

    init {
        // A small software layer gives the same 4dp blurred shadow as the iOS button.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true
    }

    fun setIconBitmap(bmp: Bitmap) {
        iconBitmap = bmp
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val padding = dp(6).toFloat()
        val radius = minOf(w, h) / 2f - padding

        discCx = cx
        discCy = cy + dp(1).toFloat()
        discRadius = radius

        bgPaint.shader = android.graphics.LinearGradient(
            0f, discCy - radius,
            0f, discCy + radius,
            intArrayOf(NativeColors.card, NativeColors.pickerControl),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )

        ringPaint.strokeWidth = dp(1).coerceAtLeast(1).toFloat()

        val iconSize = dp(23).toFloat()
        iconRect.set(
            discCx - iconSize / 2f,
            discCy - iconSize / 2f,
            discCx + iconSize / 2f,
            discCy + iconSize / 2f,
        )
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        pressedScale = if (pressed) 0.94f else 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (discRadius <= 0f) return
        canvas.save()
        canvas.scale(pressedScale, pressedScale, discCx, discCy)

        canvas.drawCircle(discCx, discCy, discRadius, shadowPaint)
        canvas.drawCircle(discCx, discCy, discRadius, bgPaint)
        canvas.drawCircle(discCx, discCy, discRadius, ringPaint)

        iconBitmap?.let { bmp ->
            if (!bmp.isRecycled) canvas.drawBitmap(bmp, null, iconRect, iconPaint)
        }
        canvas.restore()
    }
}
