package com.axoo.features.posts.domain

enum class VoteState(val apiValue: String?) {
    None(null),
    Upvote("upvote"),
    Downvote("downvote");

    val toInt: Int
        get() = when (this) {
            Upvote -> 1
            Downvote -> -1
            None -> 0
        }

    companion object {
        fun fromString(value: String?): VoteState = when (value) {
            "upvote" -> Upvote
            "downvote" -> Downvote
            else -> None
        }
    }
}
