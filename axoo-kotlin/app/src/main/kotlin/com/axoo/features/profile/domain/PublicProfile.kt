package com.axoo.features.profile.domain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PublicProfile(
    val id: String,
    val nickname: String,
    val bio: String = "",
    @Json(name = "university_id") val universityId: String,
    @Json(name = "university_name") val universityName: String = "",
    @Json(name = "avatar_config") val avatarConfig: Map<String, Any>? = null,
    @Json(name = "bio_entities") val bioEntities: List<com.axoo.engine.emoji.text.MessageEntity> = emptyList(),
    @Json(name = "is_profile_public") val isProfilePublic: Boolean = false,
)
