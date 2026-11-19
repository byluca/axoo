package com.axoo.engine.emoji.text

import com.axoo.engine.emoji.catalog.Emoji
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTextMapperTest {
    @Test
    fun toEditorState_preserves_fallback_text_and_entity_ranges() {
        val state = EmojiTextMapper.toEditorState(
            text = "Hi :) and <3",
            entities = listOf(
                MessageEntity(offset = 3, length = 2, customEmojiId = 1),
                MessageEntity(offset = 10, length = 2, customEmojiId = 2),
            ),
            emojiById = emptyMap(),
        )

        assertEquals("Hi :) and <3", state.text)
        assertEquals(
            listOf(
                MessageEntity(offset = 3, length = 2, customEmojiId = 1),
                MessageEntity(offset = 10, length = 2, customEmojiId = 2),
            ),
            state.entities,
        )
    }

    @Test
    fun toEditorState_uses_catalog_fallback_for_object_replacement_text() {
        val state = EmojiTextMapper.toEditorState(
            text = "${EmojiTextMapper.OBJECT_REPLACEMENT_CHAR_STRING} ok",
            entities = listOf(MessageEntity(offset = 0, length = 1, customEmojiId = 7)),
            emojiById = mapOf(7 to emoji(id = 7, fallback = "🔥")),
        )

        assertEquals("🔥 ok", state.text)
        assertEquals(
            listOf(MessageEntity(offset = 0, length = 2, customEmojiId = 7)),
            state.entities,
        )
    }

    @Test
    fun extractCurrentState_restores_text_and_entities_from_spans() {
        val text = "A ${EmojiTextMapper.OBJECT_REPLACEMENT_CHAR_STRING} B"
        val span = AnimatedEmojiSpan(
            renderSizePx = 16,
            emojiIndex = 4,
            emojiName = "heart",
            fallbackChar = "❤️",
        )

        val result = EmojiTextMapper.extractCurrentState(
            text = text,
            spans = listOf(span),
            spanStart = { 2 },
            spanEnd = { 3 },
            emojiById = mapOf(4 to emoji(id = 4, fallback = "❤️")),
        )

        assertEquals("A ❤️ B", result.text)
        assertEquals(
            listOf(MessageEntity(offset = 2, length = 2, customEmojiId = 4)),
            result.entities,
        )
    }

    @Test
    fun toEditorState_preserves_static_unicode_text() {
        val entity = MessageEntity(
            type = MessageEntity.TYPE_STATIC_EMOJI,
            offset = 2,
            length = 2,
            customEmojiId = 1600,
        )
        val state = EmojiTextMapper.toEditorState(
            text = "A 😀 B",
            entities = listOf(entity),
            emojiById = emptyMap(),
        )

        assertEquals("A 😀 B", state.text)
        assertEquals(listOf(entity), state.entities)
    }

    private fun emoji(id: Int, fallback: String): Emoji = Emoji(
        id = id,
        name = "emoji-$id",
        fallback = fallback,
    )
}
