package com.axoo.features.profile.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateBioRequest(
    val bio: String,
    @Json(name = "bio_entities") val bioEntities: List<com.axoo.engine.emoji.text.MessageEntity> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DeleteAccountRequest(val confirmation: String = "DELETE")

@JsonClass(generateAdapter = true)
data class UpdateAvatarRequest(
    @Json(name = "avatar_config") val avatarConfig: Map<String, Any>,
)

@JsonClass(generateAdapter = true)
data class UpdateNicknameRequest(val nickname: String)
