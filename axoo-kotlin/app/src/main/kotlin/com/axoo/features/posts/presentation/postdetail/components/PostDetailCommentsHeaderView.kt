package com.axoo.features.posts.presentation.postdetail.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.view.View
import com.axoo.ui.components.cardPixelsPerPoint
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import kotlin.math.ceil

/** Static comments heading, with the same geometry as iOS. */
internal class PostDetailCommentsHeaderView(context: Context) : View(context) {
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = context.baloo2Typeface(800)
        color = NativeColors.textPrimary
    }
    private var pixelsPerPoint = context.cardPixelsPerPoint()
    private var titleBaseline = 0f
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        pixelsPerPoint = context.cardPixelsPerPoint(width)
        titlePaint.textSize = 22f * pixelsPerPoint
        titleBaseline = 8f * pixelsPerPoint - titlePaint.ascent()
        val height = titlePaint.descent() - titlePaint.ascent() + 16f * pixelsPerPoint
        setMeasuredDimension(width, ceil(height.toDouble()).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText("Commenti", 16f * pixelsPerPoint, titleBaseline, titlePaint)
    }
}
