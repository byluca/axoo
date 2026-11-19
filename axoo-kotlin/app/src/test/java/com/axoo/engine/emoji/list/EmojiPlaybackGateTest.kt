package com.axoo.engine.emoji.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPlaybackGateTest {

    @Test
    fun playback_denied_when_detached() {
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.FullyVisible,
                isAttachedToWindow = false,
            ),
        )
    }

    @Test
    fun playback_allowed_only_when_all_axes_align() {
        assertTrue(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.FullyVisible,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun inactive_screen_blocks_playback() {
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.Inactive,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun active_transition_blocks_playback() {
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.ActiveFrozen,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun interactive_transition_keeps_playback_running() {
        assertTrue(
            computeEmojiPlaybackAllowed(
                gate = EmojiPlaybackGate.InteractiveTransition,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun offscreen_child_surface_blocks_playback() {
        val child = EmojiPlaybackGate.FullyVisible.withSurfaceFullyVisible(false)
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = child,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun visible_child_does_not_unfreeze_parent() {
        val child = EmojiPlaybackGate.ActiveFrozen.withSurfaceFullyVisible(true)
        assertFalse(
            computeEmojiPlaybackAllowed(
                gate = child,
                isAttachedToWindow = true,
            ),
        )
    }

    @Test
    fun interactive_transition_still_respects_child_surface_visibility() {
        val visibleChild = EmojiPlaybackGate.InteractiveTransition.withSurfaceFullyVisible(true)
        val hiddenChild = EmojiPlaybackGate.InteractiveTransition.withSurfaceFullyVisible(false)

        assertTrue(computeEmojiPlaybackAllowed(visibleChild, isAttachedToWindow = true))
        assertFalse(computeEmojiPlaybackAllowed(hiddenChild, isAttachedToWindow = true))
    }
}
