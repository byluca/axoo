package com.axoo.engine.emoji.text

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiOnlyClassifierTest {
    @Test
    fun classify_returnsNormalTextWhenWordsArePresent() {
        assertEquals(
            EmojiTextMode.NormalText,
            EmojiOnlyClassifier.classify("ciao 🔥", emptyList()),
        )
    }

    @Test
    fun classify_countsFromOneToFiveUnicodeEmojis() {
        val expectedModes = listOf(
            EmojiTextMode.OneEmoji,
            EmojiTextMode.TwoEmoji,
            EmojiTextMode.ThreeEmoji,
            EmojiTextMode.FourEmoji,
            EmojiTextMode.FiveEmoji,
        )

        expectedModes.forEachIndexed { index, expected ->
            assertEquals(
                expected,
                EmojiOnlyClassifier.classify("🔥".repeat(index + 1), emptyList()),
            )
        }
    }

    @Test
    fun classify_ignoresWhitespaceAroundEmojis() {
        assertEquals(
            EmojiTextMode.TwoEmoji,
            EmojiOnlyClassifier.classify("  🔥  🎉\n", emptyList()),
        )
    }

    @Test
    fun classify_returnsNormalTextForMoreThanFiveEmojis() {
        assertEquals(
            EmojiTextMode.NormalText,
            EmojiOnlyClassifier.classify("🔥".repeat(6), emptyList()),
        )
    }

    @Test
    fun classify_countsCustomEmojiEntities() {
        val entity = MessageEntity(
            type = MessageEntity.TYPE_CUSTOM_EMOJI,
            offset = 0,
            length = 1,
            customEmojiId = 7,
        )

        assertEquals(
            EmojiTextMode.OneEmoji,
            EmojiOnlyClassifier.classify("\uFFFC", listOf(entity)),
        )
    }
}
