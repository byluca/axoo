package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.lottie.LottieHost
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageReceiverTest {

    @Test
    fun receiver_loads_publishes_and_draws_for_attached_host() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val receiverRef = AtomicReference<ImageReceiver>()
        val host = RecordingHost()

        instrumentation.runOnMainSync {
            host.attached = true
            val receiver = ImageReceiver(context, host).apply {
                setSize(96, 96)
                setImage(AnimatedEmojiKey(id = "fire", path = ASSET, sizePx = 96))
                onAttachedToWindow()
                play()
            }
            receiverRef.set(receiver)
        }

        assertTrue(
            "ImageReceiver did not publish a frame",
            waitUntil { receiverRef.get().hasDrawableContent },
        )

        val output = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        try {
            instrumentation.runOnMainSync {
                receiverRef.get().draw(
                    canvas = Canvas(output),
                    bounds = RectF(0f, 0f, 96f, 96f),
                )
                receiverRef.get().onDetachedFromWindow()
                host.attached = false
            }
            assertTrue(host.invalidations > 0)
            assertTrue(output.containsVisiblePixels())
        } finally {
            output.recycle()
        }
    }

    @Test
    fun rapid_detach_and_reattach_keeps_the_ready_frame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val receiverRef = AtomicReference<ImageReceiver>()
        val host = RecordingHost()

        instrumentation.runOnMainSync {
            host.attached = true
            receiverRef.set(
                ImageReceiver(context, host).apply {
                    setSize(96, 96)
                    setImage(AnimatedEmojiKey(id = "fire", path = ASSET, sizePx = 96))
                    onAttachedToWindow()
                    play()
                },
            )
        }
        assertTrue(
            "ImageReceiver did not publish a frame",
            waitUntil { receiverRef.get().hasDrawableContent },
        )

        repeat(20) {
            instrumentation.runOnMainSync {
                val receiver = receiverRef.get()
                receiver.onDetachedFromWindow()
                host.attached = false
                host.attached = true
                receiver.onAttachedToWindow()

                val output = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                try {
                    receiver.draw(Canvas(output), RectF(0f, 0f, 96f, 96f))
                    assertTrue("Ready frame was lost after detach $it", output.containsVisiblePixels())
                } finally {
                    output.recycle()
                }
            }
        }

        instrumentation.runOnMainSync {
            receiverRef.get().onDetachedFromWindow()
            host.attached = false
            receiverRef.get().setImage(null)
        }
    }

    private class RecordingHost : LottieHost {
        var attached = false
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
