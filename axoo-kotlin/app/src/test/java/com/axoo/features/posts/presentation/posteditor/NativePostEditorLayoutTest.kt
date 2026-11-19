package com.axoo.features.posts.presentation.posteditor

import com.axoo.features.posts.presentation.posteditor.components.postEditorAvailableBodyHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class NativePostEditorLayoutTest {
    @Test
    fun pickerAndSystemInsetAreRemovedFromWritingSpace() {
        assertEquals(
            592,
            postEditorAvailableBodyHeight(
                totalHeight = 1000,
                topBarHeight = 80,
                bodyTopMargin = 8,
                toolbarHeight = 56,
                pickerHeight = 240,
                bottomOffset = 24,
            ),
        )
    }

    @Test
    fun bodyHeightNeverBecomesNegative() {
        assertEquals(
            0,
            postEditorAvailableBodyHeight(
                totalHeight = 300,
                topBarHeight = 80,
                bodyTopMargin = 8,
                toolbarHeight = 56,
                pickerHeight = 240,
                bottomOffset = 24,
            ),
        )
    }
}
