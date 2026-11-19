package com.axoo.engine.emoji.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telegram-style global policy: scroll state is diagnostic only and never
 * participates in the playback gate.
 */
class EmojiScrollStatePlaybackTest {

    @Test
    fun scroll_states_do_not_gate_playback() {
        for (state in listOf(
            EmojiScrollState.IDLE,
            EmojiScrollState.DRAGGING,
            EmojiScrollState.SETTLING,
            EmojiScrollState.PROGRAMMATIC_SCROLL,
        )) {
            assertTrue(
                "scroll state $state must not gate playback",
                computeEmojiPlaybackAllowed(
                    gate = EmojiPlaybackGate.FullyVisible,
                    isAttachedToWindow = true,
                ),
            )
        }
    }

    @Test
    fun detached_view_still_denies_playback() {
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.FullyVisible,
                isAttachedToWindow = false,
            ),
        )
    }

    @Test
    fun navigation_freeze_still_denies_playback() {
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.ActiveFrozen,
                isAttachedToWindow = true,
            ),
        )
    }
}
