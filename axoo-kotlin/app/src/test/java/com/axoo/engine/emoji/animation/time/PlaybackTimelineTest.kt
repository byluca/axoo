package com.axoo.engine.emoji.animation.time

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimelineTest {
    @Test
    fun small_emoji_sampling_keeps_the_original_loop_duration() {
        val sampled = PlaybackTimeline(totalFrames = 120, frameRate = 60f, maximumFrameRate = 45f)
        assertEquals(4, sampled.frameAt(0, 70_000_000L))
        assertEquals(4, sampled.frameAt(0, 80_000_000L))
        assertEquals(8, sampled.frameAt(0, 140_000_000L))
        assertEquals(0, sampled.frameAt(0, 2_000_000_000L))
        assertEquals(6, PlaybackTimeline(24, 12f, 45f).frameAt(0, 500_000_000L))
    }

    private val timeline = PlaybackTimeline(
        totalFrames = 60,
        frameRate = 30f,
    )

    @Test
    fun starts_from_requested_frame() {
        assertEquals(12, timeline.frameAt(startFrame = 12, elapsedNanos = 0L))
    }

    @Test
    fun converts_elapsed_time_to_frame() {
        assertEquals(
            15,
            timeline.frameAt(
                startFrame = 0,
                elapsedNanos = 500_000_000L,
            ),
        )
    }

    @Test
    fun loops_after_last_frame() {
        assertEquals(
            0,
            timeline.frameAt(
                startFrame = 0,
                elapsedNanos = 2_000_000_000L,
            ),
        )
    }

    @Test
    fun skipped_time_skips_intermediate_frames() {
        assertEquals(
            45,
            timeline.frameAt(
                startFrame = 0,
                elapsedNanos = 1_500_000_000L,
            ),
        )
    }
}
