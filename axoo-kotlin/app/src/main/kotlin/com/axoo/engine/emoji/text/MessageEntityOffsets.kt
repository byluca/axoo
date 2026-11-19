package com.axoo.engine.emoji.text

/** Converte gli offset Android (UTF-16) nel formato Unicode usato dal server. */
internal fun List<MessageEntity>.toApiEntityOffsets(text: String): List<MessageEntity> =
    mapNotNull { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        runCatching {
            entity.copy(
                offset = text.codePointCount(0, start),
                length = text.codePointCount(start, end),
            )
        }.getOrNull()
    }

/** Converte gli offset Unicode del server negli indici UTF-16 usati da Android. */
internal fun List<MessageEntity>.toUiEntityOffsets(text: String): List<MessageEntity> =
    mapNotNull { entity ->
        runCatching {
            val start = text.offsetByCodePoints(0, entity.offset)
            val end = text.offsetByCodePoints(start, entity.length)
            if (start < 0 || end < start || end > text.length) return@mapNotNull null
            entity.copy(offset = start, length = end - start)
        }.getOrNull()
    }
