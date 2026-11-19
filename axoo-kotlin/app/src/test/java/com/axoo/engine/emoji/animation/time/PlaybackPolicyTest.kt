package com.axoo.engine.emoji.animation.time

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun small_emoji_threshold_is_density_independent() {
        for (density in listOf(1f, 1.5f, 2.625f, 3f)) {
            val threshold = (32 * density).toInt()
            assertEquals(45f, PlaybackPolicy.maximumFrameRate((16 * density).toInt(), density), 0f)
            assertEquals(45f, PlaybackPolicy.maximumFrameRate(threshold, density), 0f)
            assertEquals(60f, PlaybackPolicy.maximumFrameRate(threshold + 1, density), 0f)
            assertEquals(60f, PlaybackPolicy.maximumFrameRate((64 * density).toInt(), density), 0f)
        }
    }

    private val configuration = PlaybackPolicy.Configuration(
        maxConcurrent = 3,
    )

    private fun candidate(
        id: Long,
        ready: Boolean = true,
        area: Int = 10_000,
        centerY: Float = 50f,
        visiblePermille: Int = 1_000,
        top: Int = 0,
        left: Int = 0,
    ) = PlaybackPolicy.Candidate(
        id = id,
        value = "emoji-$id",
        ready = ready,
        visibleAreaPx = area,
        visibleCenterY = centerY,
        visiblePermille = visiblePermille,
        top = top,
        left = left,
        stableOrder = id.toInt(),
    )

    @Test
    fun disallowed_playback_selects_nothing() {
        val decision = PlaybackPolicy.decide(
            candidates = listOf(candidate(1), candidate(2)),
            currentActiveIds = setOf(1L, 2L),
            playbackAllowed = false,
            configuration = configuration.copy(maxConcurrent = 2),
            viewportCenterY = 100f,
        )

        assertTrue(decision.desiredIds.isEmpty())
        assertTrue(decision.toStart.isEmpty())
    }

    @Test
    fun cap_prefers_ready_cells_with_largest_visible_area() {
        val decision = PlaybackPolicy.decide(
            candidates = listOf(
                candidate(1, area = 4_000),
                candidate(2, area = 10_000),
                candidate(3, ready = false, area = 20_000),
                candidate(4, area = 8_000),
            ),
            currentActiveIds = emptySet(),
            playbackAllowed = true,
            configuration = configuration.copy(maxConcurrent = 2),
            viewportCenterY = 100f,
        )

        assertEquals(setOf(2L, 4L), decision.desiredIds)
    }

    @Test
    fun barely_visible_cells_only_fill_unused_slots() {
        val decision = PlaybackPolicy.decide(
            candidates = listOf(
                candidate(1, area = 9_000, visiblePermille = 900),
                candidate(2, area = 2_000, visiblePermille = 200),
                candidate(3, area = 1_000, visiblePermille = 100),
            ),
            currentActiveIds = emptySet(),
            playbackAllowed = true,
            configuration = configuration,
            viewportCenterY = 100f,
        )

        assertEquals(setOf(1L, 2L, 3L), decision.desiredIds)
    }

    @Test
    fun removed_ids_are_excluded_and_only_new_cells_start_in_screen_order() {
        val decision = PlaybackPolicy.decide(
            candidates = listOf(
                candidate(3, top = 100, left = 100),
                candidate(4, top = 100, left = 0),
                candidate(5, top = 0, left = 0),
            ),
            currentActiveIds = setOf(1L, 3L),
            playbackAllowed = true,
            configuration = configuration,
            viewportCenterY = 100f,
        )

        assertEquals(listOf("emoji-5", "emoji-4"), decision.toStart.map { it.value })
        assertEquals(listOf(5L, 4L), decision.toStart.map { it.id })
        assertEquals(setOf(3L, 4L, 5L), decision.desiredIds)
    }

}
