/*
 * Riconosce i testi formati soltanto da emoji.
 *
 * Conta fino a cinque emoji, incluse quelle personalizzate descritte dalle
 * entità. La schermata che usa il risultato decide poi le dimensioni grafiche.
 */
package com.axoo.engine.emoji.text

internal enum class EmojiTextMode {
    NormalText,
    OneEmoji,
    TwoEmoji,
    ThreeEmoji,
    FourEmoji,
    FiveEmoji,
}

internal object EmojiOnlyClassifier {

    fun classify(body: String, entities: List<MessageEntity>): EmojiTextMode {
        if (body.isEmpty()) return EmojiTextMode.NormalText

        val emojiRanges = entities
            .asSequence()
            .filter { it.type == MessageEntity.TYPE_CUSTOM_EMOJI || it.type == MessageEntity.TYPE_STATIC_EMOJI }
            .filter { it.offset >= 0 && it.length > 0 && it.offset + it.length <= body.length }
            .map { IntRange(it.offset, it.offset + it.length - 1) }
            .toList()

        var emojiCount = 0
        var i = 0
        while (i < body.length) {
            val range = emojiRanges.firstOrNull { i in it }
            if (range != null) {
                emojiCount++
                if (emojiCount > 5) return EmojiTextMode.NormalText
                i = range.last + 1
                continue
            }

            val cp = body.codePointAt(i)
            val charCount = Character.charCount(cp)

            if (!isIgnorable(cp)) {
                if (isUnicodeEmojiCodePoint(cp)) {
                    emojiCount++
                    if (emojiCount > 5) return EmojiTextMode.NormalText
                } else {
                    return EmojiTextMode.NormalText
                }
            }

            i += charCount
        }

        return when (emojiCount) {
            1 -> EmojiTextMode.OneEmoji
            2 -> EmojiTextMode.TwoEmoji
            3 -> EmojiTextMode.ThreeEmoji
            4 -> EmojiTextMode.FourEmoji
            5 -> EmojiTextMode.FiveEmoji
            else -> EmojiTextMode.NormalText
        }
    }

    private fun isIgnorable(cp: Int): Boolean {
        if (cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E) return true
        if (cp in 0x1F3FB..0x1F3FF) return true
        val type = Character.getType(cp).toByte()
        return when (type) {
            Character.SPACE_SEPARATOR,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
            Character.CONTROL,
            Character.FORMAT -> true
            else -> false
        }
    }

    private fun isUnicodeEmojiCodePoint(cp: Int): Boolean {
        return when (cp) {
            in 0x1F300..0x1FAFF,
            in 0x2600..0x27BF,
            in 0x1F1E6..0x1F1FF,
            in 0x2300..0x23FF,
            in 0x2B00..0x2BFF,
            in 0x1F000..0x1F2FF,
            in 0x3030..0x303D,
            in 0x3297..0x3299 -> true
            else -> false
        }
    }
}
