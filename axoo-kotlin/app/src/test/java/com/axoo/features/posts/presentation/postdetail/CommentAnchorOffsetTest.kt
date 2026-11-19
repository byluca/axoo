package com.axoo.features.posts.presentation.postdetail

import com.axoo.features.posts.presentation.postdetail.components.commentAnchorOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentAnchorOffsetTest {
    @Test
    fun selectedCommentIsPlacedAboveComposer() {
        assertEquals(
            288,
            commentAnchorOffset(
                viewportHeight = 500,
                paddingTop = 0,
                paddingBottom = 0,
                commentHeight = 200,
                bottomGap = 12,
            ),
        )
    }

    @Test
    fun tallCommentStartsAtTopInsteadOfLeavingTheViewport() {
        assertEquals(
            0,
            commentAnchorOffset(
                viewportHeight = 300,
                paddingTop = 4,
                paddingBottom = 0,
                commentHeight = 420,
                bottomGap = 12,
            ),
        )
    }
}
