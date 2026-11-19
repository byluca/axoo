package com.axoo.engine.emoji.animation.time

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedEmojiPlaybackClockTest {

    private val timeline = PlaybackTimeline(totalFrames = 60, frameRate = 30f)

    @After
    fun tearDown() {
        AnimatedEmojiPlaybackClock.clearForTests()
    }

    @Test
    fun same_key_uses_same_phase_for_late_joiners() {
        assertEquals(
            0,
            AnimatedEmojiPlaybackClock.frameAt(
                key = "fire:asset",
                timeline = timeline,
                frameTimeNanos = 1_000_000_000L,
            ),
        )

        assertEquals(
            15,
            AnimatedEmojiPlaybackClock.frameAt(
                key = "fire:asset",
                timeline = timeline,
                frameTimeNanos = 1_500_000_000L,
            ),
        )
    }

    @Test
    fun different_keys_have_independent_phase() {
        AnimatedEmojiPlaybackClock.frameAt(
            key = "fire:asset",
            timeline = timeline,
            frameTimeNanos = 1_000_000_000L,
        )

        assertEquals(
            0,
            AnimatedEmojiPlaybackClock.frameAt(
                key = "joy:asset",
                timeline = timeline,
                frameTimeNanos = 1_500_000_000L,
            ),
        )
    }

}
