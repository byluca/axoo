package com.axoo.engine.emoji.animation.drawable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.lottie.LottieHost
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimatedEmojiDrawableTest {

    @Test
    fun two_hosts_share_one_drawable_and_receive_the_same_frame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val drawableRef = AtomicReference<AnimatedEmojiDrawable>()
        val firstHost = RecordingHost(attached = true)
        val secondHost = RecordingHost(attached = true)

        instrumentation.runOnMainSync {
            val drawable = AnimatedEmojiDrawable(
                context = context,
                key = AnimatedEmojiKey(
                    id = "fire",
                    path = ASSET,
                    sizePx = 96,
                ),
            ).apply {
                setBounds(0, 0, 96, 96)
                attachTo(firstHost)
                attachTo(secondHost)
                requestPlayback(firstHost, true)
                requestPlayback(secondHost, true)
            }
            drawableRef.set(drawable)
        }

        assertTrue(
            "Animated emoji did not publish a frame",
            waitUntil { drawableRef.get().hasDrawableContent },
        )

        val firstOutput = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val secondOutput = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        try {
            instrumentation.runOnMainSync {
                drawableRef.get().draw(Canvas(firstOutput))
                drawableRef.get().draw(Canvas(secondOutput))
                drawableRef.get().detachFrom(firstHost)
                assertTrue(drawableRef.get().isAttachedForLottie)
                drawableRef.get().detachFrom(secondHost)
                assertTrue(!drawableRef.get().isAttachedForLottie)
                drawableRef.get().dispose()
            }

            assertTrue(firstHost.invalidations > 0)
            assertTrue(secondHost.invalidations > 0)
            assertTrue(firstOutput.containsVisiblePixels())
            assertTrue(secondOutput.containsVisiblePixels())
        } finally {
            firstOutput.recycle()
            secondOutput.recycle()
        }
    }

    @Test
    fun paused_attachment_stays_unloaded_until_play_and_keeps_its_frame_on_pause() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var receiver: ImageReceiver
        instrumentation.runOnMainSync {
            receiver = ImageReceiver(instrumentation.targetContext, RecordingHost(attached = true))
            receiver.setSize(96, 96)
            receiver.setImage(AnimatedEmojiKey("rocket", ASSET, 96))
            receiver.onAttachedToWindow()
            assertTrue(!receiver.isAnimationLoaded)
            assertTrue(!receiver.isLoadingAnimation)
            assertTrue(!receiver.hasDrawableContent)
            receiver.play()
        }
        try {
            assertTrue(waitUntil { receiver.hasDrawableContent })
            instrumentation.runOnMainSync {
                receiver.pause()
                assertTrue(receiver.isAnimationLoaded)
                assertTrue(receiver.hasDrawableContent)
            }
        } finally {
            instrumentation.runOnMainSync {
                receiver.onDetachedFromWindow()
                receiver.setImage(null)
            }
        }
    }

    private class RecordingHost(
        var attached: Boolean,
    ) : LottieHost {
        var invalidations = 0

        override val isAttachedForLottie: Boolean
            get() = attached

        override fun invalidateLottie(bounds: Rect?) {
            invalidations += 1
        }
    }

    private fun Bitmap.containsVisiblePixels(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val result = AtomicReference(false)
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { result.set(condition()) }
            if (result.get()) return true
            SystemClock.sleep(25)
        }
        return false
    }

    private companion object {
        const val ASSET = "axooji/animations/focaccia-peroni/rocket.tgs"
    }
}
