package com.axoo.features.posts.presentation.postdetail.components

import com.axoo.engine.emoji.text.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentEmojiPayloadTrimTest {
    @Test
    fun trimCommentEmojiPayload_shiftsEntitiesAfterLeadingWhitespace() {
        val result = trimCommentEmojiPayload(
            text = "  ok ❤️  ",
            entities = listOf(MessageEntity(offset = 5, length = 2, customEmojiId = 9)),
        )

        assertEquals("ok ❤️", result.text)
        assertEquals(listOf(MessageEntity(offset = 3, length = 2, customEmojiId = 9)), result.entities)
    }

    @Test
    fun trimCommentEmojiPayload_dropsEntitiesIntersectingTrimmedWhitespace() {
        val result = trimCommentEmojiPayload(
            text = "  ok  ",
            entities = listOf(
                MessageEntity(offset = 1, length = 1, customEmojiId = 1),
                MessageEntity(offset = 3, length = 1, customEmojiId = 2),
                MessageEntity(offset = 4, length = 2, customEmojiId = 3),
            ),
        )

        assertEquals("ok", result.text)
        assertEquals(listOf(MessageEntity(offset = 1, length = 1, customEmojiId = 2)), result.entities)
    }
}
