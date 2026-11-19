package com.axoo.features.posts.presentation.posteditor

import com.axoo.features.posts.presentation.composer.ComposerPanelState
import com.axoo.features.posts.presentation.posteditor.components.shouldKeepEditorEmojiSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEmojiSlotStateTest {
    @Test
    fun staleGeometryCannotKeepEmojiVisibleAfterHiddenRequest() {
        assertFalse(
            shouldKeepEditorEmojiSlot(
                emojiRequested = false,
                composerPanel = ComposerPanelState.Hidden,
                panelShouldShowPicker = true,
            ),
        )
    }

    @Test
    fun emojiRemainsVisibleOnlyDuringKeyboardHandoff() {
        assertTrue(
            shouldKeepEditorEmojiSlot(
                emojiRequested = false,
                composerPanel = ComposerPanelState.Keyboard,
                panelShouldShowPicker = true,
            ),
        )
    }

    @Test
    fun explicitEmojiRequestAlwaysKeepsEmojiSlot() {
        assertTrue(
            shouldKeepEditorEmojiSlot(
                emojiRequested = true,
                composerPanel = ComposerPanelState.Emoji,
                panelShouldShowPicker = false,
            ),
        )
    }
}
