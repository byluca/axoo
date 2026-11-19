package com.axoo.features.posts.presentation.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomInputPanelControllerTest {

    @Test
    fun expandedKeyboardDoesNotIncreasePickerHeight() {
        assertEquals(820, stableImePanelHeight(savedHeightPx = 820, candidateHeightPx = 1_150))
    }

    @Test
    fun smallerStableKeyboardUpdatesPickerHeight() {
        assertEquals(760, stableImePanelHeight(savedHeightPx = 820, candidateHeightPx = 760))
    }

    @Test
    fun firstStableKeyboardHeightBecomesTheBaseline() {
        assertEquals(820, stableImePanelHeight(savedHeightPx = 0, candidateHeightPx = 820))
    }

    @Test
    fun hiddenKeyboardKeepsTheLastKnownHeight() {
        assertEquals(820, stableImePanelHeight(savedHeightPx = 820, candidateHeightPx = 0))
    }
}
