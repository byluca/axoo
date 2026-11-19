package com.axoo.features.events.domain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CityEvent(
    val id: String,
    val title: String,
    val description: String,
    @Json(name = "cover_url") val coverUrl: String,
    @Json(name = "organizer_name") val organizerName: String,
    @Json(name = "organizer_logo_url") val organizerLogoUrl: String = "",
    @Json(name = "starts_at") val startsAt: String,
    @Json(name = "ends_at") val endsAt: String,
    val venue: String,
    val address: String,
    @Json(name = "entry_info") val entryInfo: String,
    @Json(name = "organizer_id") val organizerId: String? = null,
    @Json(name = "organizer_avatar_config") val organizerAvatarConfig: Map<String, Any>? = null,
    @Json(name = "can_manage") val canManage: Boolean = false,
    val cancelled: Boolean = false,
)


@JsonClass(generateAdapter = true)
data class EventInput(
    val title: String,
    val description: String,
    @Json(name = "cover_url") val coverUrl: String,
    @Json(name = "starts_at") val startsAt: String,
    @Json(name = "ends_at") val endsAt: String,
    val venue: String,
    val address: String,
    @Json(name = "entry_info") val entryInfo: String,
)
