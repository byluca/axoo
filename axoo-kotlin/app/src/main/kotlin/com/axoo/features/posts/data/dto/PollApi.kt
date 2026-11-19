package com.axoo.features.posts.data.dto

import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.domain.PostPoll
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePollRequest(val options: List<PollOptionInput>)

@JsonClass(generateAdapter = true)
data class PollVoteRequest(@Json(name = "option_id") val optionId: String)

@JsonClass(generateAdapter = true)
data class PollVoteResponse(val poll: PostPoll)
