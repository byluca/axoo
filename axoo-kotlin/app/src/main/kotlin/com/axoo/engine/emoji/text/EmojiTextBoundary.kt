package com.axoo.engine.emoji.text

internal object EmojiTextBoundary {
    private const val ZeroWidthJoiner = '\u200D'
    private const val VariationSelectorStart = 0xFE00
    private const val VariationSelectorEnd = 0xFE0F
    private const val SkinToneStart = 0x1F3FB
    private const val SkinToneEnd = 0x1F3FF
    private const val RegionalIndicatorStart = 0x1F1E6
    private const val RegionalIndicatorEnd = 0x1F1FF

    fun previousGraphemeStart(text: CharSequence, selectionStart: Int): Int {
        if (selectionStart <= 0) return 0
        var start = previousCodePointStart(text, selectionStart)

        if (isRegionalIndicator(codePointAt(text, start))) {
            val previous = previousCodePointStart(text, start)
            if (previous < start && isRegionalIndicator(codePointAt(text, previous))) {
                return previous
            }
            return start
        }

        while (start > 0) {
            val codePoint = codePointAt(text, start)
            val previous = previousCodePointStart(text, start)
            val previousCodePoint = codePointAt(text, previous)

            if (isVariationSelector(codePoint) || isSkinTone(codePoint)) {
                start = previous
                continue
            }

            if (previousCodePoint == ZeroWidthJoiner.code) {
                val beforeJoiner = previousCodePointStart(text, previous)
                if (beforeJoiner < previous) {
                    start = beforeJoiner
                    continue
                }
            }

            break
        }

        return start
    }

    private fun previousCodePointStart(text: CharSequence, index: Int): Int {
        val rawStart = (index - 1).coerceAtLeast(0)
        return if (
            rawStart > 0 &&
            text[rawStart].isLowSurrogate() &&
            text[rawStart - 1].isHighSurrogate()
        ) {
            rawStart - 1
        } else {
            rawStart
        }
    }

    private fun codePointAt(text: CharSequence, index: Int): Int {
        if (index < 0 || index >= text.length) return -1
        val first = text[index]
        return if (
            first.isHighSurrogate() &&
            index + 1 < text.length &&
            text[index + 1].isLowSurrogate()
        ) {
            Character.toCodePoint(first, text[index + 1])
        } else {
            first.code
        }
    }

    private fun isVariationSelector(codePoint: Int): Boolean =
        codePoint in VariationSelectorStart..VariationSelectorEnd

    private fun isSkinTone(codePoint: Int): Boolean =
        codePoint in SkinToneStart..SkinToneEnd

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in RegionalIndicatorStart..RegionalIndicatorEnd
}
