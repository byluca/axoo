package com.axoo.ui.formatting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAgoTest {
    @Test
    fun contentIsEditedOnlyWhenServerUpdateIsAfterCreation() {
        val created = "2026-08-02T10:00:00Z"

        assertFalse(isContentEdited(created, null))
        assertFalse(isContentEdited(created, created))
        assertFalse(isContentEdited(created, "data non valida"))
        assertTrue(isContentEdited(created, "2026-08-02T10:00:01Z"))
    }
}
