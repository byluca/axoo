package com.axoo.features.posts.presentation.postdetail.components

import com.axoo.engine.emoji.text.MessageEntity

data class CommentTrimmedEmojiPayload(
    val text: String,
    val entities: List<MessageEntity>,
)

fun trimCommentEmojiPayload(text: String, entities: List<MessageEntity>): CommentTrimmedEmojiPayload {
    val leading = text.length - text.trimStart().length
    val trimmed = text.trim()
    val trailingStart = leading + trimmed.length
    val adjusted = entities.mapNotNull { entity ->
        val start = entity.offset
        val end = start + entity.length
        if (start < leading || end > trailingStart) return@mapNotNull null
        entity.copy(offset = start - leading)
    }
    return CommentTrimmedEmojiPayload(trimmed, adjusted)
}
