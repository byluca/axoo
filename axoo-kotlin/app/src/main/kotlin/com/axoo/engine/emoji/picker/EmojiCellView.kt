package com.axoo.engine.emoji.picker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiDrawable
import com.axoo.engine.emoji.list.EmojiFrameCoordinator
import com.axoo.engine.lottie.LottieHost

/** Draws only prepared animation frames; an unloaded cell stays empty. */
class EmojiCellView(
    context: Context,
    private val emojiVisualSizePx: Int = 0,
) : View(context), LottieHost {
    private companion object {
        const val DEFAULT_EMOJI_VISUAL_SCALE = 0.74f
        const val MAX_EMOJI_VISUAL_SCALE = 0.92f
    }

    private val emojiBounds = Rect()
    private var animatedEmojiDrawable: AnimatedEmojiDrawable? = null
    private var frameCoordinator: EmojiFrameCoordinator? = null

    init {
        isClickable = true
        isFocusable = true
    }

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow

    override fun invalidateLottie(bounds: Rect?) {
        if (!isAttachedForLottie) return
        frameCoordinator?.requestInvalidate(this) ?: invalidate()
    }

    fun setFrameCoordinator(value: EmojiFrameCoordinator?) {
        if (frameCoordinator === value) return
        frameCoordinator?.cancel(this)
        frameCoordinator = value
    }

    internal fun setAnimatedEmojiDrawable(
        drawable: AnimatedEmojiDrawable?,
        requestInvalidate: Boolean = true,
    ) {
        if (animatedEmojiDrawable === drawable) return
        animatedEmojiDrawable = drawable
        if (requestInvalidate) invalidateLottie()
    }

    fun clearContent(requestInvalidate: Boolean = false) {
        setAnimatedEmojiDrawable(null, requestInvalidate = false)
        if (requestInvalidate) invalidateLottie()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animatedEmojiDrawable?.refreshHostAttachment()
    }

    override fun onDetachedFromWindow() {
        animatedEmojiDrawable?.refreshHostAttachment()
        frameCoordinator?.cancel(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        computeEmojiVisualBounds(emojiBounds)
        val drawable = animatedEmojiDrawable
        if (drawable?.hasDrawableContent == true) {
            drawable.draw(canvas, emojiBounds)
        }
    }

    internal fun computeEmojiVisualBounds(out: Rect) {
        val cellSize = minOf(width, height).coerceAtLeast(1)
        val maxSize = (cellSize * MAX_EMOJI_VISUAL_SCALE).toInt().coerceAtLeast(1)
        val requestedSize = if (emojiVisualSizePx > 0) {
            emojiVisualSizePx
        } else {
            (cellSize * DEFAULT_EMOJI_VISUAL_SCALE).toInt()
        }
        val targetSize = requestedSize.coerceIn(1, maxSize)
        val left = (width - targetSize) / 2
        val top = (height - targetSize) / 2
        out.set(left, top, left + targetSize, top + targetSize)
    }
}
