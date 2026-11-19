package com.axoo.engine.emoji.animation.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiPlaybackStallTrackerTest {
    private val tracker = EmojiPlaybackStallTracker()

    @Test
    fun `reports stall only after six seconds without published frames`() {
        assertNull(tracker.sample(true, 0, 0))
        assertNull(tracker.sample(true, 0, 5_999))
        assertEquals(EmojiPlaybackStallTracker.Event.STALLED, tracker.sample(true, 0, 6_000))
        assertNull(tracker.sample(true, 0, 12_000))
    }

    @Test
    fun `published frames reset the timeout even when the animation loops`() {
        assertNull(tracker.sample(true, 1, 0))
        assertNull(tracker.sample(true, 51, 4_000))
        assertNull(tracker.sample(true, 101, 8_000))
        assertNull(tracker.sample(true, 101, 13_999))
        assertEquals(EmojiPlaybackStallTracker.Event.STALLED, tracker.sample(true, 101, 14_000))
    }

    @Test
    fun `reports recovery once and can detect a later stall`() {
        tracker.sample(true, 0, 0)
        tracker.sample(true, 0, 6_000)
        assertEquals(EmojiPlaybackStallTracker.Event.RECOVERED, tracker.sample(true, 1, 8_000))
        assertNull(tracker.sample(true, 2, 10_000))
        assertEquals(EmojiPlaybackStallTracker.Event.STALLED, tracker.sample(true, 2, 16_000))
    }

    @Test
    fun `pause or background does not count toward the timeout`() {
        tracker.sample(true, 10, 0)
        assertNull(tracker.sample(false, 10, 4_000))
        assertNull(tracker.sample(false, 10, 40_000))
        assertNull(tracker.sample(true, 10, 42_000))
        assertNull(tracker.sample(true, 10, 47_999))
        assertEquals(EmojiPlaybackStallTracker.Event.STALLED, tracker.sample(true, 10, 48_000))
    }

    @Test
    fun `pausing a stalled player does not report a false recovery`() {
        tracker.sample(true, 0, 0)
        tracker.sample(true, 0, 6_000)
        assertNull(tracker.sample(false, 0, 8_000))
        assertNull(tracker.sample(true, 1, 10_000))
    }
}
