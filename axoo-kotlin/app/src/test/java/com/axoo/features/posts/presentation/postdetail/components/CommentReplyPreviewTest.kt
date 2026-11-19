package com.axoo.features.posts.presentation.postdetail.components

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentReplyPreviewTest {
    @Test
    fun deletedReplyNeverBuildsAnimatedSpansFromStaleEntities() {
        val comment = Comment(id = "reply", postId = "post", userId = "me", body = "😀",
            entities = listOf(MessageEntity(offset = 0, length = 2, customEmojiId = 1218)),
            isDeleted = true, createdAt = "2026-10-03T12:00:00Z")
        assertEquals("Commento non disponibile", buildCommentReplyText(comment, emojiSizePx = 16))
    }
}
