package com.axoo.application.navigation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationScrollTrackerTest {
    private val tracker = BottomNavigationScrollTracker(thresholdPx = 24)

    @Test
    fun hidesOnlyAfterEnoughDownwardScroll() {
        assertNull(tracker.onScrolled(12))
        assertTrue(tracker.isVisible)

        assertEquals(false, tracker.onScrolled(12))
        assertFalse(tracker.isVisible)
    }

    @Test
    fun showsOnlyAfterEnoughUpwardScroll() {
        tracker.onScrolled(24)

        assertNull(tracker.onScrolled(-12))
        assertFalse(tracker.isVisible)

        assertEquals(true, tracker.onScrolled(-12))
        assertTrue(tracker.isVisible)
    }

    @Test
    fun changingDirectionRestartsTheThreshold() {
        tracker.onScrolled(20)

        assertNull(tracker.onScrolled(-5))
        assertNull(tracker.onScrolled(20))
        assertTrue(tracker.isVisible)
        assertEquals(false, tracker.onScrolled(4))
    }

    @Test
    fun continuedScrollInTheSameDirectionDoesNotRepeatTheDecision() {
        assertEquals(false, tracker.onScrolled(24))
        assertNull(tracker.onScrolled(100))
        assertFalse(tracker.isVisible)
    }
}
