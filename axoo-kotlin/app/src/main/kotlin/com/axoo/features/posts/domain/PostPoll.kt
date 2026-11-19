package com.axoo.features.posts.domain

import com.axoo.engine.emoji.text.MessageEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val MIN_POLL_OPTIONS = 2
const val MAX_POLL_OPTIONS = 4
const val MAX_POLL_OPTION_CHARACTERS = 100

@JsonClass(generateAdapter = true)
data class PollOptionInput(
    val text: String = "",
    val entities: List<MessageEntity> = emptyList(),
)

fun isPollInputValid(options: List<PollOptionInput>): Boolean =
    options.size in MIN_POLL_OPTIONS..MAX_POLL_OPTIONS && options.all {
        it.text.isNotBlank() && it.text.codePointCount(0, it.text.length) <= MAX_POLL_OPTION_CHARACTERS
    }

@JsonClass(generateAdapter = true)
data class PostPoll(
    val options: List<PostPollOption> = emptyList(),
    @Json(name = "total_votes") val totalVotes: Int = 0,
    @Json(name = "my_option_id") val myOptionId: String? = null,
    @Transient val isSending: Boolean = false,
) {
    val hasVoted: Boolean get() = myOptionId != null

    fun withOptimisticVote(optionId: String): PostPoll {
        if (hasVoted || isSending || options.none { it.id == optionId }) return this
        return copy(
            options = options.map { if (it.id == optionId) it.copy(votes = it.votes + 1) else it },
            totalVotes = totalVotes + 1,
            myOptionId = optionId,
            isSending = true,
        )
    }

    // Largest remainder rounding keeps displayed percentages at exactly 100%.
    fun percentages(): List<Int> {
        val total = options.sumOf { it.votes.coerceAtLeast(0).toLong() }
        if (total == 0L) return options.map { 0 }
        val scaled = options.map { it.votes.coerceAtLeast(0).toLong() * 100 }
        val values = scaled.map { (it / total).toInt() }.toMutableList()
        val remaining = 100 - values.sum()
        scaled.indices.sortedByDescending { scaled[it] % total }.take(remaining).forEach { values[it]++ }
        return values
    }
}

@JsonClass(generateAdapter = true)
data class PostPollOption(
    val id: String,
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
    val votes: Int = 0,
    val voters: List<PollVoter> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PollVoter(
    @Json(name = "user_id") val userId: String,
    @Json(name = "avatar_config") val avatarConfig: Map<String, Any>? = null,
)
