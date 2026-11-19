package com.axoo.features.posts.presentation.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.axoo.ui.theme.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One square crop, with bounded pan and pinch. The original photo stays on the device. */
internal class StickerCropView(context: Context) : View(context) {
    var image: Bitmap? = null
        set(value) { field = value; resetCrop(); invalidate() }
    private val crop = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = 0x80000000.toInt() }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var showGrid = false
    private var scale = 1f
    private var minimumScale = 1f
    private var left = 0f
    private var top = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (scale * detector.scaleFactor).coerceIn(minimumScale, minimumScale * 4)
            val ratio = next / scale
            left = detector.focusX - (detector.focusX - left) * ratio
            top = detector.focusY - (detector.focusY - top) * ratio
            scale = next
            clamp()
            invalidate()
            return true
        }
    })

    init {
        background = GradientDrawable().apply {
            setColor(0xFF16161A.toInt())
            cornerRadius = context.dp(20).toFloat()
        }
        clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = (min(w, h) - context.dp(32)).coerceAtLeast(0).toFloat()
        crop.set((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        resetCrop()
    }

    private fun resetCrop() {
        val bitmap = image ?: return
        if (crop.isEmpty) return
        minimumScale = max(crop.width() / bitmap.width, crop.height() / bitmap.height)
        scale = minimumScale
        left = crop.centerX() - bitmap.width * scale / 2
        top = crop.centerY() - bitmap.height * scale / 2
    }

    private fun clamp() {
        val bitmap = image ?: return
        left = left.coerceIn(crop.right - bitmap.width * scale, crop.left)
        top = top.coerceIn(crop.bottom - bitmap.height * scale, crop.top)
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = image ?: return
        if (crop.isEmpty) return
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), paint)
        // Keep the surrounding photo visible, dimmed outside the fixed square.
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, dimPaint)
        canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, dimPaint)
        canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, dimPaint)
        guidePaint.strokeWidth = resources.displayMetrics.density
        guidePaint.color = 0x99FFFFFF.toInt()
        canvas.drawRect(crop, guidePaint)
        if (showGrid) {
            guidePaint.color = 0x66FFFFFF
            for (third in 1..2) {
                val x = crop.left + crop.width() * third / 3
                val y = crop.top + crop.height() * third / 3
                canvas.drawLine(x, crop.top, x, crop.bottom, guidePaint)
                canvas.drawLine(crop.left, y, crop.right, y, guidePaint)
            }
        }
        guidePaint.color = Color.WHITE
        guidePaint.strokeWidth = context.dp(2).toFloat()
        val corner = context.dp(16).toFloat()
        for (horizontal in 0..1) for (vertical in 0..1) {
            val x = if (horizontal == 0) crop.left else crop.right
            val y = if (vertical == 0) crop.top else crop.bottom
            canvas.drawLine(x, y, x + if (horizontal == 0) corner else -corner, y, guidePaint)
            canvas.drawLine(x, y, x, y + if (vertical == 0) corner else -corner, guidePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || image == null) return false
        showGrid = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        invalidate()
        parent?.requestDisallowInterceptTouchEvent(true)
        pinch.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !pinch.isInProgress && event.pointerCount == 1) {
            left += event.x - lastX
            top += event.y - lastY
            clamp()
            invalidate()
        }
        lastX = event.x
        lastY = event.y
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun cropped(): Bitmap {
        val bitmap = requireNotNull(image)
        check(!crop.isEmpty)
        val side = (crop.width() / scale).roundToInt().coerceIn(1, min(bitmap.width, bitmap.height))
        val x = ((crop.left - left) / scale).roundToInt().coerceIn(0, bitmap.width - side)
        val y = ((crop.top - top) / scale).roundToInt().coerceIn(0, bitmap.height - side)
        val region = Bitmap.createBitmap(bitmap, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(region, 512, 512, true)
        if (region !== bitmap && region !== scaled) region.recycle()
        return if (scaled === bitmap) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
    }
}
