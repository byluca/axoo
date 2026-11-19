package com.axoo.features.moderation.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModerationActionRequest(
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String = "",
    @Json(name = "duration_hours") val durationHours: Int = 0,
    @Json(name = "source_target_type") val sourceTargetType: String? = null,
    @Json(name = "source_target_id") val sourceTargetId: String? = null,
)
