package com.axoo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCountBadgeTest {

    @Test
    fun formatsCountsForBothSharedBadges() {
        assertEquals("1", unreadCountBadgeText(1))
        assertEquals("99", unreadCountBadgeText(99))
        assertEquals("99+", unreadCountBadgeText(100))
    }
}
