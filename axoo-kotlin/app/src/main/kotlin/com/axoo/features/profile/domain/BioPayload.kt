package com.axoo.features.profile.domain

import com.axoo.engine.emoji.text.MessageEntity

data class BioPayload(val text: String, val entities: List<MessageEntity>)

fun trimBioPayload(text: String, entities: List<MessageEntity>): BioPayload {
    val leading = text.length - text.trimStart().length
    val trimmed = text.trim()
    val end = leading + trimmed.length
    return BioPayload(trimmed, entities.mapNotNull { entity ->
        if (entity.offset < leading || entity.length <= 0 || entity.offset > end - entity.length) null
        else entity.copy(offset = entity.offset - leading)
    })
}
