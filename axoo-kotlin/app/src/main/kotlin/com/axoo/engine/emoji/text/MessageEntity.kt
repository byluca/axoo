/**
 * Rappresenta un elemento speciale (es. custom emoji) all'interno del testo.
 * Mantenuta leggera per ottimizzare l'uso della RAM, definisce solo offset e tipo.
 */
package com.axoo.engine.emoji.text

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessageEntity(
    val type: String = "custom_emoji",
    val offset: Int,
    val length: Int,
    @Json(name = "custom_emoji_id") val customEmojiId: Int,
    @Json(name = "user_id") val userId: String? = null,
) {
    companion object {
        const val TYPE_CUSTOM_EMOJI = "custom_emoji"
        const val TYPE_STATIC_EMOJI = "static_emoji"
    }
}
