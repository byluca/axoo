package com.axoo.features.posts.domain

data class VoteResult(val upvotes: Int, val downvotes: Int, val myVote: String)

/**
 * L'API di voto riceve uno stato desiderato assoluto: 1, 0 o -1. Ripetere la
 * stessa richiesta è quindi intenzionalmente idempotente sul contratto del client.
 */
fun desiredVote(previous: VoteState, requested: String): VoteState {
    val selected = VoteState.fromString(requested)
    return if (previous == selected) VoteState.None else selected
}

fun Post.withOptimisticVote(next: VoteState): Post = copy(
    upvotes = upvotes - (if (myVote == VoteState.Upvote) 1 else 0) +
        (if (next == VoteState.Upvote) 1 else 0),
    downvotes = downvotes - (if (myVote == VoteState.Downvote) 1 else 0) +
        (if (next == VoteState.Downvote) 1 else 0),
    myVoteRaw = next.apiValue,
)

fun Comment.withOptimisticVote(next: VoteState): Comment = copy(
    upvotes = upvotes - (if (myVote == VoteState.Upvote) 1 else 0) +
        (if (next == VoteState.Upvote) 1 else 0),
    downvotes = downvotes - (if (myVote == VoteState.Downvote) 1 else 0) +
        (if (next == VoteState.Downvote) 1 else 0),
    myVoteRaw = next.apiValue,
)

fun Post.withServerVote(result: VoteResult): Post = copy(
    upvotes = result.upvotes,
    downvotes = result.downvotes,
    myVoteRaw = result.myVote,
)

fun Comment.withServerVote(result: VoteResult): Comment = copy(
    upvotes = result.upvotes,
    downvotes = result.downvotes,
    myVoteRaw = result.myVote,
)
