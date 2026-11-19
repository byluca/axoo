package com.axoo.engine.emoji.text

import com.axoo.engine.emoji.catalog.Emoji

internal data class EditorTextState(
    val text: String,
    val entities: List<MessageEntity>,
)

// Traduttore bidirezionale: converte i dati delle emoji dal formato "App" al formato nativo di Android (e viceversa).
internal object EmojiTextMapper {
    // Il carattere speciale "invisibile" di Android. Dice alla casella di testo: "Lascia un buco qui, ci andrà un'immagine".
    const val OBJECT_REPLACEMENT_CHAR_STRING = "\uFFFC"

    private data class ExtractedSpan(
        val start: Int,
        val end: Int,
        val emojiId: Int,
        val fallback: String,
    )

    // DAL SERVER -> AD ANDROID: Prende il testo grezzo e le coordinate, e infila i "buchi invisibili" (partendo dal fondo per non sballare le posizioni).
    fun toEditorState(
        text: String,
        entities: List<MessageEntity>,
        emojiById: Map<Int, Emoji>,
    ): EditorTextState {
        if (entities.isEmpty()) return EditorTextState(text, emptyList())

        val valid = entities
            .filter {
                (it.type == MessageEntity.TYPE_CUSTOM_EMOJI ||
                    it.type == MessageEntity.TYPE_STATIC_EMOJI) &&
                    it.offset >= 0 &&
                    it.length > 0 &&
                    it.customEmojiId >= 0 &&
                    it.offset + it.length <= text.length
            }
            .sortedBy(MessageEntity::offset)

        val fallbackByEntity = valid.associateWith { entity ->
            val source = text.substring(entity.offset, entity.offset + entity.length)
            source.takeUnless {
                it == OBJECT_REPLACEMENT_CHAR_STRING.repeat(entity.length)
            } ?: emojiById[entity.customEmojiId]?.fallback.orEmpty()
                .ifBlank { OBJECT_REPLACEMENT_CHAR_STRING }
        }

        val builder = StringBuilder(text)
        valid.asReversed().forEach { entity ->
            builder.replace(
                entity.offset,
                entity.offset + entity.length,
                fallbackByEntity.getValue(entity),
            )
        }

        var offsetDelta = 0
        val adjusted = valid.map { entity ->
            val fallback = fallbackByEntity.getValue(entity)
            entity.copy(
                offset = entity.offset + offsetDelta,
                length = fallback.length,
            ).also {
                offsetDelta += fallback.length - entity.length
            }
        }
        return EditorTextState(builder.toString(), adjusted)
    }

    // DA ANDROID -> AL SERVER: Fa l'esatto opposto. Legge la casella di testo, trova i buchi invisibili e ricalcola le coordinate per il nostro database.
    fun extractCurrentState(
        text: CharSequence?,
        spans: List<AnimatedEmojiSpan>,
        spanStart: (AnimatedEmojiSpan) -> Int,
        spanEnd: (AnimatedEmojiSpan) -> Int,
        emojiById: Map<Int, Emoji>,
    ): EditorTextState {
        val editable = text ?: return EditorTextState("", emptyList())
        val sortedSpans = spans
            .map {
                ExtractedSpan(
                    start = spanStart(it),
                    end = spanEnd(it),
                    emojiId = it.emojiIndex,
                    fallback = it.fallbackChar,
                )
            }
            .sortedBy(ExtractedSpan::start)

        val builder = StringBuilder()
        val entities = mutableListOf<MessageEntity>()
        var currentIndex = 0

        for (span in sortedSpans) {
            if (span.start < currentIndex || span.start < 0 || span.end <= span.start) continue
            if (span.emojiId < 0) continue

            builder.append(
                editable.substring(currentIndex, span.start)
                    .replace(OBJECT_REPLACEMENT_CHAR_STRING, span.fallback),
            )
            val entityOffset = builder.length
            val fallback = span.fallback.ifBlank {
                editable.substring(span.start, span.end)
                    .replace(
                        OBJECT_REPLACEMENT_CHAR_STRING,
                        emojiById[span.emojiId]?.fallback.orEmpty(),
                    )
            }
            builder.append(fallback)
            entities += MessageEntity(
                offset = entityOffset,
                length = fallback.length,
                customEmojiId = span.emojiId,
            )
            currentIndex = span.end
        }

        if (currentIndex < editable.length) {
            builder.append(
                editable.substring(currentIndex, editable.length)
                    .replace(OBJECT_REPLACEMENT_CHAR_STRING, ""),
            )
        }
        return EditorTextState(builder.toString(), entities)
    }

}
