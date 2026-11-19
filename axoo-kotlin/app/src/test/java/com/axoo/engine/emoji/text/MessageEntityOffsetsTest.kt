package com.axoo.engine.emoji.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageEntityOffsetsTest {

    @Test
    fun server_offsets_are_converted_after_a_unicode_emoji() {
        val text = "😀🔥"
        val serverEntity = MessageEntity(
            offset = 1,
            length = 1,
            customEmojiId = 269,
        )

        val converted = listOf(serverEntity).toUiEntityOffsets(text)

        assertEquals(
            listOf(serverEntity.copy(offset = 2, length = 2)),
            converted,
        )
    }

    @Test
    fun android_offsets_round_trip_through_the_server_format() {
        val text = "Ciao 😀 e 🔥!"
        val androidEntity = MessageEntity(
            offset = 10,
            length = 2,
            customEmojiId = 269,
        )

        val result = listOf(androidEntity)
            .toApiEntityOffsets(text)
            .toUiEntityOffsets(text)

        assertEquals(listOf(androidEntity), result)
    }

    @Test
    fun invalid_server_offsets_are_ignored() {
        val invalid = MessageEntity(offset = 99, length = 1, customEmojiId = 269)

        assertEquals(emptyList<MessageEntity>(), listOf(invalid).toUiEntityOffsets("ciao"))
    }
}
