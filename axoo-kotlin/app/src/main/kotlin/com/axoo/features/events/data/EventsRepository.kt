package com.axoo.features.events.data

import com.axoo.features.posts.domain.ImageMeta
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import javax.inject.Inject

@JsonClass(generateAdapter = true)
data class CampusEvent(
    val id: String,
    val title: String,
    val location: String,
    val description: String,
    @Json(name = "starts_at") val startsAt: String,
    val poster: ImageMeta? = null,
    @Json(name = "organizer_id") val organizerId: String? = null,
    @Json(name = "organizer_name") val organizerName: String? = null,
    @Json(name = "organizer_avatar_config") val organizerAvatarConfig: Map<String, Any>? = null,
    @Json(name = "can_manage") val canManage: Boolean = false,
    @Json(name = "ends_at") val endsAt: String? = null,
    val address: String = "",
    @Json(name = "entry_info") val entryInfo: String = "",
)

@JsonClass(generateAdapter = true)
data class EventsPage(
    val events: List<CampusEvent>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
    @Json(name = "can_manage") val canManage: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class EventInput(
    val title: String,
    val location: String,
    val description: String,
    @Json(name = "starts_at") val startsAt: String,
    val poster: ImageMeta? = null,
    @Json(name = "ends_at") val endsAt: String? = null,
    val address: String = "",
    @Json(name = "entry_info") val entryInfo: String = "",
)

class EventsRepository @Inject constructor(private val api: EventsApi) {
    suspend fun event(id: String) = api.getEvent(id)
    suspend fun update(id: String, input: EventInput) = api.updateEvent(id,input)
    suspend fun page(cursor: String?) = api.getEvents(cursor)
    suspend fun create(input: EventInput) = api.createEvent(input)
    suspend fun delete(id: String) = api.deleteEvent(id)
}

fun CampusEvent.toCityEvent(canManage: Boolean = this.canManage) = com.axoo.features.events.domain.CityEvent(
    id = id, title = title, description = description, coverUrl = poster?.url.orEmpty(),
    organizerName = organizerName?.takeIf(String::isNotBlank) ?: "Organizzatore",
    startsAt = startsAt, endsAt = endsAt.orEmpty(), venue = location, address = address, entryInfo = entryInfo,
    organizerId = organizerId, organizerAvatarConfig = organizerAvatarConfig, canManage = canManage,
)
