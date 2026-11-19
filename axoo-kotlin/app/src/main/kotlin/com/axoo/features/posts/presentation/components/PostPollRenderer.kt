package com.axoo.features.posts.presentation.components

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.axoo.core.anim.AxooInterpolators
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.engine.emoji.list.EmojiFrameCoordinator
import com.axoo.engine.emoji.text.AnimatedEmojiRenderer
import com.axoo.features.posts.domain.PostPoll
import com.axoo.features.posts.domain.PostPollOption
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.interTypeface
import kotlin.math.roundToInt

/** Poll drawing and motion belong to the shared post card, including emoji playback. */
internal class PostPollRenderer(private val host: View) {
    private val context get() = host.context
    private var scale = 1f
    private fun p(value: Float) = value * scale
    private var poll: PostPoll? = null
    private val rows = mutableListOf<Row>()
    val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.interTypeface(400) }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.interTypeface(500) }
    private val tmp = RectF()
    private val clip = Path()
    private var percentages = emptyList<Int>()
    private var barFractions = emptyList<Float>()
    private var selectionProgress = 0f
    private var animator: ValueAnimator? = null
    private var animating = false
    private var coordinator: EmojiFrameCoordinator? = null

    fun bind(value: PostPoll?, sameItem: Boolean) {
        val previous = poll
        if (sameItem && previous == value) return
        val reuse = sameItem && rows.map { it.option.id } == value?.options?.map { it.id }
        if (!reuse) { clearRows(); value?.options?.forEach { rows.add(Row(it)) } }
        else rows.forEachIndexed { index, row -> row.option = value!!.options[index] }
        poll = value
        percentages = value?.percentages().orEmpty()
        val target = value?.options?.map {
            if (value.hasVoted && !value.isSending && value.totalVotes > 0) it.votes.toFloat() / value.totalVotes else 0f
        }.orEmpty()
        val from = if (reuse) barFractions else target
        val selectionFrom = if (reuse) selectionProgress else if (value?.hasVoted == true) 1f else 0f
        val selectionTo = if (value?.hasVoted == true) 1f else 0f
        animator?.cancel()
        val canAnimate = reuse && host.isAttachedToWindow &&
            (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) &&
            (from != target || selectionFrom != selectionTo)
        if (canAnimate) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 240
                interpolator = AxooInterpolators.Decelerate
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    barFractions = target.mapIndexed { index, end ->
                        val start = from.getOrElse(index) { 0f }
                        start + (end - start) * progress
                    }
                    selectionProgress = selectionFrom + (selectionTo - selectionFrom) * progress
                    host.invalidate()
                }
                start()
            }
        } else { barFractions = target; selectionProgress = selectionTo }
        host.invalidate()
    }

    fun layout(left: Float, top: Float, right: Float, pixelsPerPoint: Float): Float {
        scale = pixelsPerPoint
        if (poll == null) { bounds.setEmpty(); return top }
        textPaint.textSize = context.sp(16f)
        smallPaint.textSize = context.sp(11f)
        val textWidth = (right - left - p(48f)).toInt().coerceAtLeast(1)
        var y = top
        rows.forEach { row ->
            val size = (textPaint.textSize * MIXED_TEXT_EMOJI_SCALE).roundToInt()
            if (row.width != textWidth || row.emojiSize != size || row.layout == null) {
                row.width = textWidth
                row.emojiSize = size
                row.text = buildNativeEmojiText(row.option.text, row.option.entities, size, row.option.id)
                row.layout = textPaint.staticLayout(row.text, textWidth, MIXED_TEXT_LINE_SPACING)
                row.renderer.updateFromText(row.text)
                row.renderer.setAnimating(animating && host.isAttachedToWindow)
            }
            row.textLeft = left + p(36f)
            row.textTop = y + maxOf(p(23f), smallPaint.descent() - smallPaint.ascent() + p(6f))
            val height = maxOf(p(74f), row.textTop - y + (row.layout?.height ?: 0) + p(30f))
            row.bounds.set(left, y, right, y + height)
            row.square.set(left + p(6f), row.textTop + p(1f), left + p(24f), row.textTop + p(19f))
            row.barY = row.textTop + (row.layout?.height ?: 0) + p(12f)
            y += height
        }
        val totalHeight = smallPaint.descent() - smallPaint.ascent()
        bounds.set(left, top, right, y + totalHeight + p(18f))
        return bounds.bottom
    }

    fun draw(canvas: Canvas) {
        val value = poll ?: return
        val hasVoted = value.hasVoted && !value.isSending
        val accent = NativeColors.accent
        textPaint.color = NativeColors.textPrimary
        smallPaint.color = NativeColors.textSecondary
        rows.forEachIndexed { index, row ->
            val selected = value.myOptionId == row.option.id
            if (selected && selectionProgress > 0f) {
                clip.reset()
                clip.addRoundRect(row.bounds, p(10f), p(10f), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                paint.color = accent.withAlpha(22)
                tmp.set(row.bounds.left, row.bounds.top, row.bounds.left + row.bounds.width() * selectionProgress, row.bounds.bottom)
                canvas.drawRect(tmp, paint)
                canvas.restore()
            }
            if (index > 0) {
                paint.color = NativeColors.divider
                paint.strokeWidth = p(0.7f).coerceAtLeast(1f)
                canvas.drawLine(row.bounds.left, row.bounds.top, row.bounds.right, row.bounds.top, paint)
            }
            if (hasVoted) {
                smallPaint.color = if (selected) accent else NativeColors.textSecondary
                val percentage = "${percentages.getOrElse(index) { 0 }}%"
                canvas.drawText(percentage, row.square.centerX() - smallPaint.measureText(percentage) / 2f,
                    row.square.top - p(7f), smallPaint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = p(1f).coerceAtLeast(1f)
            paint.color = if (selected) accent else NativeColors.textSecondary
            canvas.drawRoundRect(row.square, p(4f), p(4f), paint)
            if (selected) {
                paint.style = Paint.Style.FILL
                paint.color = accent
                canvas.drawRoundRect(row.square, p(4f), p(4f), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = p(1.8f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = Color.WHITE
                clip.reset()
                clip.moveTo(row.square.left + p(4f), row.square.top + p(9f))
                clip.lineTo(row.square.left + p(8f), row.square.top + p(13f))
                clip.lineTo(row.square.left + p(14f), row.square.top + p(5f))
                canvas.drawPath(clip, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.save()
            canvas.translate(row.textLeft, row.textTop)
            row.renderer.beforeTextDraw(row.layout, row.text)
            row.layout?.draw(canvas)
            row.renderer.draw(canvas)
            canvas.restore()
            if (hasVoted) drawResults(canvas, row, index)
        }
        smallPaint.color = NativeColors.textSecondary
        val label = when {
            value.isSending -> "Invio del voto…"
            value.totalVotes == 1 -> "1 voto"
            else -> "${value.totalVotes} voti"
        }
        canvas.drawText(label, bounds.left + p(6f), bounds.bottom - p(8f) - smallPaint.descent(), smallPaint)
    }

    private fun drawResults(canvas: Canvas, row: Row, index: Int) {
        val voters = row.option.voters.take(3)
        val hidden = (row.option.votes - voters.size).coerceAtLeast(0)
        val overflow = if (hidden > 0) "+$hidden" else ""
        val overflowWidth = if (overflow.isEmpty()) 0f else smallPaint.measureText(overflow) + p(6f)
        val avatarsWidth = if (voters.isEmpty()) 0f else p(20f) + p(14f) * (voters.size - 1)
        val avatarLeft = row.bounds.right - p(6f) - overflowWidth - avatarsWidth
        val barRight = (avatarLeft - p(10f)).coerceAtLeast(row.textLeft + p(12f))
        tmp.set(row.textLeft, row.barY - p(1.5f), barRight, row.barY + p(1.5f))
        paint.color = NativeColors.accent.withAlpha(30)
        canvas.drawRoundRect(tmp, p(1.5f), p(1.5f), paint)
        val fraction = barFractions.getOrElse(index) { 0f }.coerceIn(0f, 1f)
        tmp.right = tmp.left + tmp.width() * fraction
        paint.color = NativeColors.accent
        if (fraction > 0f) canvas.drawRoundRect(tmp, p(1.5f), p(1.5f), paint)
        voters.forEachIndexed { voterIndex, voter ->
            val left = avatarLeft + p(14f) * voterIndex
            tmp.set(left, row.barY - p(10f), left + p(20f), row.barY + p(10f))
            paint.color = NativeColors.surface
            canvas.drawOval(tmp, paint)
            val url = buildAvatarUrl(voter.userId, voter.avatarConfig)
            val size = p(48f).toInt().coerceAtLeast(1)
            val bitmap = CoilBitmapLoader.peek(context.applicationContext, url, size)
            if (bitmap != null && !bitmap.isRecycled) {
                clip.reset(); clip.addOval(tmp, Path.Direction.CW)
                canvas.save(); canvas.clipPath(clip)
                canvas.drawBitmap(bitmap, null, tmp, bitmapPaint)
                canvas.restore()
            } else {
                paint.color = NativeColors.textSecondary.withAlpha(80)
                canvas.drawCircle(tmp.centerX(), tmp.centerY(), p(5f), paint)
                CoilBitmapLoader.enqueue(context.applicationContext, url, size) { host.invalidate() }
            }
        }
        if (overflow.isNotEmpty()) {
            smallPaint.color = NativeColors.textSecondary
            canvas.drawText(overflow, row.bounds.right - p(6f) - smallPaint.measureText(overflow),
                row.barY - (smallPaint.ascent() + smallPaint.descent()) / 2f, smallPaint)
        }
    }

    fun optionAt(x: Float, y: Float): String? = rows.firstOrNull { it.bounds.contains(x, y) }?.option?.id
    fun optionBounds(index: Int): RectF? = rows.getOrNull(index)?.bounds
    fun setAnimating(value: Boolean) {
        animating = value
        rows.forEach { it.renderer.setAnimating(value && host.isAttachedToWindow) }
    }
    fun setCoordinator(value: EmojiFrameCoordinator?) {
        coordinator = value
        rows.forEach { it.renderer.setFrameCoordinator(value) }
    }
    fun isPlaying() = rows.any { it.renderer.isPlaying }
    fun refreshAttachment() = rows.forEach { it.renderer.refreshHostAttachment() }
    fun detach() { animator?.end(); setAnimating(false); refreshAttachment() }
    fun clear() { animator?.cancel(); animator = null; clearRows(); poll = null; bounds.setEmpty(); barFractions = emptyList(); selectionProgress = 0f }
    private fun clearRows() { rows.forEach { it.renderer.release() }; rows.clear() }

    private inner class Row(var option: PostPollOption) {
        val bounds = RectF()
        val square = RectF()
        var textLeft = 0f
        var textTop = 0f
        var barY = 0f
        var width = 0
        var emojiSize = 0
        var text: CharSequence = ""
        var layout: StaticLayout? = null
        val renderer = AnimatedEmojiRenderer(context.applicationContext, host).apply {
            setFrameCoordinator(coordinator)
        }
    }
}
