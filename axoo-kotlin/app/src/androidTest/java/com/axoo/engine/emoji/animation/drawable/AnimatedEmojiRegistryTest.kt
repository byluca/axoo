package com.axoo.engine.emoji.animation.drawable

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.lottie.LottieHost
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimatedEmojiRegistryTest {

    @Test
    fun same_key_reuses_drawable_after_last_lease_is_closed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val firstHost = RecordingHost(attached = true)
        val secondHost = RecordingHost(attached = true)

        instrumentation.runOnMainSync {
            val key = AnimatedEmojiKey(
                id = "registry-shared-instance",
                path = ASSET,
                sizePx = 96,
            )

            val first = AnimatedEmojiRegistry.acquire(context, key, firstHost)
            val second = AnimatedEmojiRegistry.acquire(context, key, secondHost)

            assertSame(first.drawable, second.drawable)
            first.play()
            second.play()

            first.pause()

            first.close()
            first.close()

            assertTrue(first.isClosed)
            assertFalse(second.isClosed)
            assertTrue(second.drawable.isAttachedForLottie)

            second.close()

            assertTrue(second.isClosed)
            assertFalse(second.drawable.isAttachedForLottie)

            val thirdHost = RecordingHost()
            val third = AnimatedEmojiRegistry.acquire(context, key, thirdHost)
            assertSame(first.drawable, third.drawable)
            third.close()
        }
    }

    @Test
    fun cached_drawable_keeps_its_ready_frame_for_the_next_host() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val firstHost = RecordingHost(attached = true)
        val key = AnimatedEmojiKey(
            id = "registry-ready-frame",
            path = ASSET,
            sizePx = 96,
        )

        lateinit var first: AnimatedEmojiLease
        instrumentation.runOnMainSync {
            first = AnimatedEmojiRegistry.acquire(context, key, firstHost)
            first.play()
        }

        assertTrue(
            "Animated emoji did not publish its first frame",
            waitUntil { first.drawable.hasDrawableContent },
        )

        instrumentation.runOnMainSync {
            assertTrue(first.drawable.hasDrawableContent)
            first.close()

            val secondHost = RecordingHost(attached = true)
            val second = AnimatedEmojiRegistry.acquire(context, key, secondHost)
            assertSame(first.drawable, second.drawable)
            assertTrue("Cached drawable lost its ready frame", second.drawable.hasDrawableContent)

            second.close()
        }
    }

    @Test
    fun same_host_cannot_acquire_the_same_key_twice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = RecordingHost()
        val key = AnimatedEmojiKey(
            id = "registry-duplicate-host",
            path = ASSET,
            sizePx = 96,
        )

        instrumentation.runOnMainSync {
            val lease = AnimatedEmojiRegistry.acquire(context, key, host)
            try {
                var rejected = false
                try {
                    AnimatedEmojiRegistry.acquire(context, key, host)
                } catch (_: IllegalStateException) {
                    rejected = true
                }
                assertTrue(rejected)
            } finally {
                lease.close()
            }
        }
    }

    private class RecordingHost(
        private val attached: Boolean = false,
    ) : LottieHost {
        override val isAttachedForLottie: Boolean = attached
        override fun invalidateLottie(bounds: Rect?) = Unit
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
