package com.axoo.features.profile.data.dto

import com.axoo.features.profile.domain.User
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NicknameAvailabilityResponse(val available: Boolean)

@JsonClass(generateAdapter = true)
data class BlockedUsersResponse(val users: List<User> = emptyList())
