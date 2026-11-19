package com.axoo.engine.emoji.animation.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Looper
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.emoji.animation.rendering.ImageReceiver
import com.axoo.engine.lottie.LottieHost
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Condivide un ImageReceiver tra più host e aggrega le richieste di riproduzione.
 * Gli host sono distinti per identità; le notifiche usano uno snapshot per tollerare rimozioni durante i callback.
 */
class AnimatedEmojiDrawable(
    context: Context,
    val key: AnimatedEmojiKey,
) : Drawable(), LottieHost {

    private val drawBounds = RectF()
    private val imageReceiver = ImageReceiver(
        context = context,
        host = this,
        playbackKey = key.playbackKey,
        onAnimationFrame = { invalidateHosts(frameChanged = true) },
    )

    private val hosts: MutableSet<LottieHost> = Collections.newSetFromMap(IdentityHashMap())
    private val hostInvalidationSnapshot = ArrayList<LottieHost>()
    private val playbackHosts: MutableSet<LottieHost> = Collections.newSetFromMap(IdentityHashMap())

    private var receiverAttached = false
    private var disposed = false

    val hasDrawableContent: Boolean
        get() = imageReceiver.hasDrawableContent

    override val isAttachedForLottie: Boolean
        get() = !disposed && hosts.any { it.isAttachedForLottie }

    init {
        imageReceiver.setSize(key.sizePx, key.sizePx)
        imageReceiver.setImage(key)
    }

    /** Collega una superficie esterna alla stessa istanza animata. */
    fun attachTo(host: LottieHost) {
        checkMainThread()
        check(!disposed) { "AnimatedEmojiDrawable is disposed" }
        hosts.add(host)
        updateReceiverAttachment()
        updatePlaybackState()
    }

    fun detachFrom(host: LottieHost) {
        checkMainThread()
        if (!hosts.remove(host)) return
        playbackHosts.remove(host)

        updateReceiverAttachment()
        updatePlaybackState()
    }

    /**
     * Da chiamare se un host già registrato cambia stato attached senza essere
     * aggiunto o rimosso dalla collezione.
     */
    fun refreshHostAttachment() {
        checkMainThread()
        updateReceiverAttachment()
        updatePlaybackState()
    }

    internal fun requestPlayback(host: LottieHost, play: Boolean) {
        checkMainThread()
        if (disposed || host !in hosts) return
        if (play) playbackHosts.add(host) else playbackHosts.remove(host)
        updatePlaybackState()
    }

    override fun draw(canvas: Canvas) {
        draw(canvas, bounds)
    }

    /**
     * Disegna la stessa bitmap pronta in bounds diversi senza cambiare la
     * dimensione del player condiviso.
     */
    fun draw(canvas: Canvas, bounds: Rect) {
        if (disposed || bounds.isEmpty) return
        drawBounds.set(bounds)
        imageReceiver.draw(canvas, drawBounds)
    }

    override fun invalidateLottie(bounds: Rect?) {
        invalidateHosts(frameChanged = false)
    }

    private fun invalidateHosts(frameChanged: Boolean) {
        if (!isAttachedForLottie) return
        invalidateSelf()
        hostInvalidationSnapshot.clear()
        hostInvalidationSnapshot.addAll(hosts)
        try {
            for (index in hostInvalidationSnapshot.indices) {
                val host = hostInvalidationSnapshot[index]
                if (host.isAttachedForLottie && (!frameChanged || host in playbackHosts)) {
                    host.invalidateLottie()
                }
            }
        } finally {
            hostInvalidationSnapshot.clear()
        }
    }

    override fun setAlpha(alpha: Int) {
        imageReceiver.setAlpha(alpha)
        invalidateLottie()
    }

    override fun getAlpha(): Int = imageReceiver.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        imageReceiver.setColorFilter(colorFilter)
        invalidateLottie()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = key.sizePx

    override fun getIntrinsicHeight(): Int = key.sizePx

    fun dispose() {
        checkMainThread()
        if (disposed) return
        if (receiverAttached) {
            receiverAttached = false
            imageReceiver.onDetachedFromWindow()
        }
        hosts.clear()
        hostInvalidationSnapshot.clear()
        playbackHosts.clear()

        imageReceiver.setImage(null)
        disposed = true
    }

    private fun updateReceiverAttachment() {
        if (disposed) return
        val shouldBeAttached = isAttachedForLottie
        when {
            shouldBeAttached && !receiverAttached -> {
                receiverAttached = true
                imageReceiver.onAttachedToWindow()
            }

            !shouldBeAttached && receiverAttached -> {
                receiverAttached = false
                imageReceiver.onDetachedFromWindow()
            }
        }
    }

    private fun updatePlaybackState() {
        if (disposed) return
        val hasActiveRequest = playbackHosts.any { it in hosts && it.isAttachedForLottie }
        val shouldPlay = receiverAttached && hasActiveRequest
        if (shouldPlay) imageReceiver.play() else imageReceiver.pause()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AnimatedEmojiDrawable must be changed from the main thread"
        }
    }
}
