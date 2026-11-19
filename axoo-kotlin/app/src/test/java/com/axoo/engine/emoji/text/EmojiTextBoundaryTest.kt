package com.axoo.engine.emoji.text

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTextBoundaryTest {
    @Test
    fun previousGraphemeStart_keepsSurrogatePairTogether() {
        val text = "a😀"

        assertEquals(1, EmojiTextBoundary.previousGraphemeStart(text, text.length))
    }

    @Test
    fun previousGraphemeStart_keepsFlagPairTogether() {
        val text = "x🇮🇹"

        assertEquals(1, EmojiTextBoundary.previousGraphemeStart(text, text.length))
    }

    @Test
    fun previousGraphemeStart_keepsSkinToneModifierTogether() {
        val text = "x👍🏽"

        assertEquals(1, EmojiTextBoundary.previousGraphemeStart(text, text.length))
    }

    @Test
    fun previousGraphemeStart_keepsZwjFamilyTogether() {
        val text = "x👨‍👩‍👧‍👦"

        assertEquals(1, EmojiTextBoundary.previousGraphemeStart(text, text.length))
    }
}
