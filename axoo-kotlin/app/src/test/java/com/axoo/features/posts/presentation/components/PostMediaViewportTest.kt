package com.axoo.features.posts.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PostMediaViewportTest {
    @Test
    fun landscapeMediaUsesAvailableWidthAndPreservesRatio() {
        assertEquals(
            PostMediaViewport(width = 320, height = 180),
            postMediaViewportSize(
                maxWidth = 320,
                maxHeight = 360,
                mediaWidth = 1600,
                mediaHeight = 900,
            ),
        )
    }

    @Test
    fun tallMediaUsesAvailableHeightAndBecomesNarrower() {
        assertEquals(
            PostMediaViewport(width = 202, height = 360),
            postMediaViewportSize(
                maxWidth = 320,
                maxHeight = 360,
                mediaWidth = 1080,
                mediaHeight = 1920,
            ),
        )
    }

    @Test
    fun missingDimensionsUseFourByThreeFallback() {
        assertEquals(
            PostMediaViewport(width = 320, height = 240),
            postMediaViewportSize(
                maxWidth = 320,
                maxHeight = 360,
                mediaWidth = 0,
                mediaHeight = 0,
            ),
        )
    }
}
