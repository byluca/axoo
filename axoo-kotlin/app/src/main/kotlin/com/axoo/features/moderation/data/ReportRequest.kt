package com.axoo.features.moderation.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReportRequest(
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String = "",
)
