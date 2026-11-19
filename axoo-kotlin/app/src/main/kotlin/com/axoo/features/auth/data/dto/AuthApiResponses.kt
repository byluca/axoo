package com.axoo.features.auth.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionBootstrapResponse(
    val status: String = "active",
    @Json(name = "terms_required") val termsRequired: Boolean = false,
    @Json(name = "terms_version") val termsVersion: String = "2026-10-07",
    val reason: String = "",
    @Json(name = "suspended_until") val suspendedUntil: String? = null,
    @Json(name = "moderation_notice") val moderationNotice: ModerationNoticeResponse? = null,
    @Json(name = "android_locked") val androidLocked: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ModerationNoticeResponse(
    val id: String,
    @Json(name = "target_type") val targetType: String,
    val reason: String = "",
    @Json(name = "created_at") val createdAt: String,
)
