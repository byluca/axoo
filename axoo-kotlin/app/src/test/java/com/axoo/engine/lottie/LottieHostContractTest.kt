package com.axoo.engine.lottie

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LottieHostContractTest {

    @Test
    fun host_can_be_invalidated() {
        val host = RecordingHost(attached = true)

        host.invalidateLottie()

        assertEquals(1, host.invalidateCount)
    }

    @Test
    fun detached_parent_invalidation_is_noop() {
        val parent = RecordingHost(attached = false)

        parent.invalidateLottie()

        assertEquals(0, parent.invalidateCount)
    }

    @Test
    fun attached_parent_receives_bounds() {
        val parent = RecordingHost(attached = true)
        val bounds = Rect(1, 2, 3, 4)

        parent.invalidateLottie(bounds)

        assertEquals(1, parent.invalidateCount)
        assertSame(bounds, parent.lastBounds)
        assertTrue(parent.isAttachedForLottie)
    }

    private class RecordingHost(
        private val attached: Boolean,
    ) : LottieHost {
        var invalidateCount: Int = 0
            private set
        var lastBounds: Rect? = null
            private set

        override val isAttachedForLottie: Boolean
            get() = attached

        override fun invalidateLottie(bounds: Rect?) {
            if (!isAttachedForLottie) return
            invalidateCount += 1
            lastBounds = bounds
        }
    }
}
