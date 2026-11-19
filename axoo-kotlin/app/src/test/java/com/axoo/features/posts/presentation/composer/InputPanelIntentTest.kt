package com.axoo.features.posts.presentation.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InputPanelIntentTest {
    @Test
    fun repeatedRequestIsIdempotent() {
        val emoji = InputPanelIntent().request(InputPanelTarget.Emoji)

        assertSame(emoji, emoji.request(InputPanelTarget.Emoji))
    }

    @Test
    fun emojiToKeyboardHandoffBlocksAnotherSwitchUntilImeArrives() {
        val waiting = InputPanelIntent()
            .request(InputPanelTarget.Emoji)
            .request(InputPanelTarget.Keyboard)

        assertTrue(waiting.isSwitchInProgress(imeVisible = false, imeAnimating = false))

        val settled = waiting.settleIme(imeVisible = true)
        assertFalse(settled.isSwitchInProgress(imeVisible = true, imeAnimating = false))
    }

    @Test
    fun staleKeyboardSettlementCannotOverrideLatestEmojiIntent() {
        val state = InputPanelIntent()
            .request(InputPanelTarget.Emoji)
            .request(InputPanelTarget.Keyboard)
            .request(InputPanelTarget.Emoji)
            .settleIme(imeVisible = true)

        assertEquals(InputPanelTarget.Emoji, state.target)
        assertFalse(state.awaitingKeyboard)
    }

    @Test
    fun keyboardToEmojiHandoffBlocksAnotherSwitchUntilImeCloses() {
        val state = InputPanelIntent(target = InputPanelTarget.Keyboard)
            .request(InputPanelTarget.Emoji)

        assertTrue(state.isSwitchInProgress(imeVisible = true, imeAnimating = false))
        assertFalse(state.isSwitchInProgress(imeVisible = false, imeAnimating = false))
    }

    @Test
    fun imeAnimationAlwaysSerializesPanelSwitches() {
        val state = InputPanelIntent(target = InputPanelTarget.Keyboard)

        assertTrue(state.isSwitchInProgress(imeVisible = true, imeAnimating = true))
    }

    @Test
    fun intermediateImeHeightIsNotUsableAsPickerHeight() {
        assertFalse(isUsableImePanelHeight(heightPx = 420, navInsetPx = 80, minimumPx = 504))
        assertTrue(isUsableImePanelHeight(heightPx = 780, navInsetPx = 80, minimumPx = 504))
    }
}
