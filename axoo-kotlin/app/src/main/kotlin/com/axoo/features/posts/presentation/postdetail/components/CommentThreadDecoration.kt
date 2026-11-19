package com.axoo.features.posts.presentation.postdetail.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView
import com.axoo.features.posts.presentation.components.NativeCommentView
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.cardPixelsPerPoint

/** A single background per visible thread, while each comment stays recyclable. */
internal class CommentThreadDecoration(private val context: Context) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val rows = (0 until parent.childCount).mapNotNull {
            parent.getChildAt(it) as? NativeCommentView
        }.filter { it.thread != null }.sortedBy { it.top }
        val pixelsPerPoint = context.cardPixelsPerPoint(parent.width)
        fun points(value: Float) = value * pixelsPerPoint
        for (group in rows.groupBy { it.thread!!.rootId }.values) {
            val first = group.first()
            val last = group.last()
            val radius = points(8f)
            rect.set(
                points(16f),
                if (first.thread!!.isFirst) first.top + points(4f) else minOf(first.top.toFloat(), -radius * 2),
                parent.width - points(16f),
                if (last.thread!!.isLast) last.bottom - points(4f) else maxOf(last.bottom.toFloat(), parent.height + radius * 2),
            )
            paint.style = Paint.Style.FILL
            paint.color = NativePillDefaults.backgroundColor
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = points(NativePillDefaults.borderWidthDp)
            paint.color = NativePillDefaults.borderColor
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
