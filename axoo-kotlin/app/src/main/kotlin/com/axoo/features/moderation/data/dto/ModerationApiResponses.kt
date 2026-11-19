package com.axoo.features.moderation.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModerationQueueResponse(
    val items: List<ModerationQueueItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ModerationQueueItem(
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "author_user_id") val authorUserId: String,
    @Json(name = "author_nickname") val authorNickname: String,
    val body: String,
    @Json(name = "report_count") val reportCount: Int,
    val reasons: List<String> = emptyList(),
    val action: String = "",
    @Json(name = "is_hidden") val isHidden: Boolean = false,
    @Json(name = "is_deleted") val isDeleted: Boolean = false,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_reported_at") val lastReportedAt: String,
    @Json(name = "decision_reason") val decisionReason: String = "",
    @Json(name = "author_restricted") val authorRestricted: Boolean = false,
)
