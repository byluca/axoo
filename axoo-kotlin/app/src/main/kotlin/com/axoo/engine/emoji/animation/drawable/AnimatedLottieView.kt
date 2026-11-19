package com.axoo.engine.emoji.animation.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.lottie.LottieHost

/**
 * Mostra un'animazione condivisa: acquisisce la lease all'attach e la rilascia al detach.
 */
class AnimatedLottieView(
    context: Context,
    private val animationSizePx: Int,
) : View(context), LottieHost {
    private var playbackEnabled = true
    fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled = enabled
        scheduleVisibilityUpdate()
    }
    private val drawBounds = Rect()
    private val visibleBounds = Rect()
    private var visibleForPlayback = false
    private var visibilityUpdatePosted = false
    private val visibilityUpdate = Runnable {
        visibilityUpdatePosted = false
        visibleForPlayback = isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
            getGlobalVisibleRect(visibleBounds)
        val current = lease ?: return@Runnable
        current.drawable.refreshHostAttachment()
        if (isAttachedForLottie && playbackEnabled) current.play() else current.pause()
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { scheduleVisibilityUpdate() }
    private var animationKey: AnimatedEmojiKey? = null
    private var lease: AnimatedEmojiLease? = null

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow && visibleForPlayback

    fun setAnimation(id: String, path: String) {
        val key = AnimatedEmojiKey(id, path, animationSizePx.coerceAtLeast(1))
        if (animationKey == key) return
        releaseAnimation()
        animationKey = key
        bindAnimationIfAttached()
        scheduleVisibilityUpdate()
    }

    override fun invalidateLottie(bounds: Rect?) {
        if (isAttachedForLottie) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        bindAnimationIfAttached()
        scheduleVisibilityUpdate()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        removeCallbacks(visibilityUpdate)
        visibilityUpdatePosted = false
        visibleForPlayback = false
        releaseAnimation()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        scheduleVisibilityUpdate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        scheduleVisibilityUpdate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        scheduleVisibilityUpdate()
    }

    private fun scheduleVisibilityUpdate() {
        if (!isAttachedToWindow || lease == null || visibilityUpdatePosted) return
        visibilityUpdatePosted = true
        postOnAnimation(visibilityUpdate)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height)
        if (side <= 0) return
        val left = (width - side) / 2
        val top = (height - side) / 2
        drawBounds.set(left, top, left + side, top + side)
        val drawable = lease?.drawable
        if (drawable?.hasDrawableContent == true) drawable.draw(canvas, drawBounds)
    }

    private fun bindAnimationIfAttached() {
        val key = animationKey ?: return
        if (!isAttachedToWindow || lease != null) return
        lease = AnimatedEmojiRegistry.acquire(
            context = context,
            key = key,
            host = this,
        ).also {
            if (isAttachedForLottie && playbackEnabled) it.play()
        }
    }

    private fun releaseAnimation() {
        lease?.close()
        lease = null
    }
}
