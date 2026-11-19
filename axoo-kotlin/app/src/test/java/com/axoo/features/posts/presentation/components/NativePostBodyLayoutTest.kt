package com.axoo.features.posts.presentation.components

import com.axoo.engine.emoji.text.EmojiTextMode
import com.axoo.features.posts.domain.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePostBodyLayoutTest {

    @Test
    fun buildNativeBodyText_preservesWhitespaceAndLineBreaks() {
        val body = "Prima riga\n\nSeconda  riga"

        assertEquals(
            body,
            buildNativeBodyText(post(body), emojiSizePx = 24).toString(),
        )
    }

    @Test
    fun emojiOnlySizes_decreaseFromOneToFiveEmojis() {
        val modes = listOf(
            EmojiTextMode.OneEmoji,
            EmojiTextMode.TwoEmoji,
            EmojiTextMode.ThreeEmoji,
            EmojiTextMode.FourEmoji,
            EmojiTextMode.FiveEmoji,
        )

        val emojiSizes = modes.map { emojiDpForMode(it, normalTextSp = 18f) }
        val textSizes = modes.map { bodySpForMode(it, normalTextSp = 18f) }

        assertTrue(emojiSizes.zipWithNext().all { (first, second) -> first > second })
        assertTrue(textSizes.zipWithNext().all { (first, second) -> first > second })
        assertEquals(18f, bodySpForMode(EmojiTextMode.NormalText, 18f))
    }

    private fun post(body: String) = Post(
        id = "post-id",
        userId = "user-id",
        universityId = "university-id",
        body = body,
        createdAt = "2026-08-10T10:00:00Z",
    )
}
