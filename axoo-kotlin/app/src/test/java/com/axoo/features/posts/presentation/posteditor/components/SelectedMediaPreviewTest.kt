package com.axoo.features.posts.presentation.posteditor.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedMediaPreviewTest {
    @Test
    fun landscapeMediaKeepsItsRealShape() {
        assertEquals(
            MediaPreviewViewport(width = 100, height = 56),
            mediaPreviewViewport(maxSize = 100, mediaWidth = 1600, mediaHeight = 900),
        )
    }

    @Test
    fun portraitMediaKeepsItsRealShape() {
        assertEquals(
            MediaPreviewViewport(width = 56, height = 100),
            mediaPreviewViewport(maxSize = 100, mediaWidth = 900, mediaHeight = 1600),
        )
    }

    @Test
    fun missingDimensionsUseASquareFallback() {
        assertEquals(
            MediaPreviewViewport(width = 100, height = 100),
            mediaPreviewViewport(maxSize = 100, mediaWidth = 0, mediaHeight = 0),
        )
    }
}
