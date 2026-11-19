package com.axoo.ui.components

import com.axoo.ui.theme.NativeColors
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.axoo.ui.theme.dp

internal enum class SkeletonType { Post, Comment, Notification }

/** Skeleton condivisa da feed, dettaglio post e notifiche. */
internal class NativeSkeletonCardView(
    context: Context,
    private val rows: Int,
    private val type: SkeletonType = SkeletonType.Post,
) : View(context) {
    private val cardRect = RectF()
    private val tmp = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(NativePillDefaults.borderWidthDp)
        color = NativePillDefaults.borderColor
    }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.skeleton
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (type == SkeletonType.Notification) {
            setMeasuredDimension(width, context.dp(8 + rows * 114))
            return
        }
        val gap = context.dp(8f)
        var height = gap
        repeat(rows) { index ->
            height += if (type == SkeletonType.Post) {
                if (index % 2 == 0) postCardHeight(width, hasMedia = true) else postCardHeight(width, hasMedia = false)
            } else {
                context.dp(110f)
            }
            height += gap
        }
        setMeasuredDimension(width, height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        when (type) {
            SkeletonType.Post -> drawPostSkeletons(canvas)
            SkeletonType.Comment -> drawCommentSkeletons(canvas)
            SkeletonType.Notification -> drawNotificationSkeletons(canvas)
        }
    }

    private fun drawNotificationSkeletons(canvas: Canvas) {
        val left = context.dp(12f)
        val right = width - left
        repeat(rows) { index ->
            val top = context.dp(8f + index * 114f)
            cardRect.set(left, top, right, top + context.dp(104f))
            bgPaint.color = NativePillDefaults.backgroundColor
            canvas.drawRoundRect(cardRect, context.dp(20f), context.dp(20f), bgPaint)
            canvas.drawRoundRect(cardRect, context.dp(20f), context.dp(20f), cardStrokePaint)
            drawRound(canvas, left + context.dp(16f), top + context.dp(38f), left + context.dp(44f), top + context.dp(66f), context.dp(8f))
            val text = left + context.dp(58f)
            drawRound(canvas, text, top + context.dp(20f), right - context.dp(30f), top + context.dp(32f), context.dp(6f))
            drawRound(canvas, text, top + context.dp(42f), right - context.dp(54f), top + context.dp(54f), context.dp(6f))
            drawRound(canvas, text, top + context.dp(72f), text + context.dp(54f), top + context.dp(80f), context.dp(4f))
        }
    }

    private fun drawPostSkeletons(canvas: Canvas) {

        val horizontalMargin = context.dp(16f)
        val cardRadius = context.dp(14f)
        val contentLeft = horizontalMargin + context.dp(16f)
        val avatarSize = context.dp(32f)
        val itemRadius = context.dp(7f)
        var y = context.dp(8f)

        repeat(rows) { index ->
            val hasMedia = index % 2 == 0
            val cardHeight = postCardHeight(width, hasMedia)
            val cardTop = y
            val cardBottom = y + cardHeight

            cardRect.set(horizontalMargin, cardTop, width - horizontalMargin, cardBottom)
            bgPaint.color = NativePillDefaults.backgroundColor
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStrokePaint)

            val avatarTop = cardTop + context.dp(14f)
            drawRound(canvas, contentLeft, avatarTop, contentLeft + avatarSize, avatarTop + avatarSize, avatarSize / 2f)

            val authorLeft = contentLeft + avatarSize + context.dp(12f)
            drawRound(canvas, authorLeft, cardTop + context.dp(18f), authorLeft + context.dp(86f), cardTop + context.dp(30f), itemRadius)
            drawRound(canvas, authorLeft, cardTop + context.dp(36f), authorLeft + context.dp(56f), cardTop + context.dp(46f), context.dp(5f))
            drawRound(canvas, contentLeft, cardTop + context.dp(54f), contentLeft + context.dp(72f), cardTop + context.dp(75f), context.dp(10.5f))

            val voteIconSize = context.dp(24f)
            val voteCenterX = width - horizontalMargin - context.dp(28f)
            val voteUpCenterY = cardTop + context.dp(34f)
            val voteDownCenterY = cardTop + context.dp(100f)
            drawRound(canvas, voteCenterX - voteIconSize / 2f, voteUpCenterY - voteIconSize / 2f,
                voteCenterX + voteIconSize / 2f, voteUpCenterY + voteIconSize / 2f, context.dp(4f))
            drawRound(canvas, voteCenterX - voteIconSize / 2f, voteDownCenterY - voteIconSize / 2f,
                voteCenterX + voteIconSize / 2f, voteDownCenterY + voteIconSize / 2f, context.dp(4f))

            val bodyRight = voteCenterX - voteIconSize / 2f - context.dp(8f)
            val bodyY = cardTop + context.dp(88f)
            drawRound(canvas, contentLeft, bodyY, bodyRight, bodyY + itemRadius * 2, itemRadius)
            drawRound(canvas, contentLeft, bodyY + context.dp(16f), bodyRight - context.dp(40f), bodyY + context.dp(16f) + itemRadius * 2, itemRadius)
            drawRound(canvas, contentLeft, bodyY + context.dp(32f), bodyRight - context.dp(92f), bodyY + context.dp(32f) + itemRadius * 2, itemRadius)

            if (hasMedia) {
                mediaShimmerRect(canvas, contentLeft, bodyY + context.dp(56f), bodyRight, cardBottom - context.dp(56f), context.dp(12f))
            }

            val actionsCenterY = cardBottom - context.dp(22f)
            val actionIconSize = context.dp(20f)
            drawRound(canvas, contentLeft - context.dp(4f), actionsCenterY - actionIconSize / 2f,
                contentLeft - context.dp(4f) + actionIconSize, actionsCenterY + actionIconSize / 2f, context.dp(3f))
            drawRound(canvas, contentLeft + actionIconSize + context.dp(8f), actionsCenterY - context.dp(7f),
                contentLeft + actionIconSize + context.dp(30f), actionsCenterY + context.dp(7f), context.dp(5f))
            val menuLeft = bodyRight - actionIconSize
            drawRound(canvas, menuLeft, actionsCenterY - actionIconSize / 2f,
                menuLeft + actionIconSize, actionsCenterY + actionIconSize / 2f, context.dp(3f))

            y += cardHeight + context.dp(8f)
        }
    }

    private fun drawCommentSkeletons(canvas: Canvas) {

        val horizontalMargin = context.dp(16f)
        val cardRadius = context.dp(8f)
        val contentLeft = horizontalMargin + context.dp(14f)
        val avatarSize = context.dp(26f)
        val itemRadius = context.dp(6f)
        var y = context.dp(8f)

        repeat(rows) {
            val cardTop = y
            val cardBottom = y + context.dp(110f)

            cardRect.set(horizontalMargin, cardTop, width - horizontalMargin, cardBottom)
            bgPaint.color = NativePillDefaults.backgroundColor
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStrokePaint)

            drawRound(canvas, contentLeft, cardTop + context.dp(6f), contentLeft + avatarSize,
                cardTop + context.dp(6f) + avatarSize, avatarSize / 2f)

            val headerLeft = contentLeft + avatarSize + context.dp(10f)
            drawRound(canvas, headerLeft, cardTop + context.dp(14f), headerLeft + context.dp(92f), cardTop + context.dp(26f), itemRadius)
            drawRound(canvas, headerLeft + context.dp(98f), cardTop + context.dp(16f),
                headerLeft + context.dp(142f), cardTop + context.dp(24f), context.dp(4f))

            val bodyRight = width - horizontalMargin - context.dp(14f)
            drawRound(canvas, contentLeft, cardTop + context.dp(44f), bodyRight, cardTop + context.dp(44f) + itemRadius * 2, itemRadius)
            drawRound(canvas, contentLeft, cardTop + context.dp(60f), bodyRight - context.dp(58f),
                cardTop + context.dp(60f) + itemRadius * 2, itemRadius)

            val actionsCenterY = cardBottom - context.dp(18f)
            val iconSize = context.dp(20f)
            drawRound(canvas, contentLeft + context.dp(10f), actionsCenterY - iconSize / 2f,
                contentLeft + context.dp(10f) + iconSize, actionsCenterY + iconSize / 2f, context.dp(3f))
            val downLeft = bodyRight - iconSize
            drawRound(canvas, downLeft, actionsCenterY - iconSize / 2f, downLeft + iconSize,
                actionsCenterY + iconSize / 2f, context.dp(3f))
            drawRound(canvas, downLeft - context.dp(34f), actionsCenterY - context.dp(7f),
                downLeft - context.dp(10f), actionsCenterY + context.dp(7f), context.dp(5f))
            drawRound(canvas, downLeft - context.dp(64f), actionsCenterY - iconSize / 2f,
                downLeft - context.dp(44f), actionsCenterY + iconSize / 2f, context.dp(3f))

            y += context.dp(118f)
        }
    }

    private fun postCardHeight(widthPx: Int, hasMedia: Boolean): Float {
        if (!hasMedia) return context.dp(184f)
        val mediaWidth = (widthPx - context.dp(96f)).coerceAtLeast(context.dp(160f))
        return context.dp(200f) + mediaWidth * 0.75f
    }

    private fun mediaShimmerRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ) {
        tmp.set(left, top, right, bottom)
        canvas.drawRoundRect(tmp, radius, radius, shimmerPaint)
    }

    private fun drawRound(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ) {
        tmp.set(left, top, right, bottom)
        canvas.drawRoundRect(tmp, radius, radius, shimmerPaint)
    }
}
