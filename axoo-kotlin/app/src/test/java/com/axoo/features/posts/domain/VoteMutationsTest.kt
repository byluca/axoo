package com.axoo.features.posts.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoteMutationsTest {
    private val post = Post(
        id = "post-1",
        userId = "user-1",
        universityId = "university-1",
        upvotes = 10,
        downvotes = 4,
        createdAt = "2026-07-11T12:00:00Z",
    )

    @Test
    fun repeatedAbsolutePostStateIsIdempotent() {
        val once = post.withOptimisticVote(VoteState.Upvote)
        val twice = once.withOptimisticVote(VoteState.Upvote)

        assertEquals(once, twice)
        assertEquals(11, twice.upvotes)
        assertEquals(4, twice.downvotes)
    }

    @Test
    fun postTapSequenceKeepsOnlyTheLatestDesiredState() {
        val up = post.withOptimisticVote(desiredVote(post.myVote, "upvote"))
        val none = up.withOptimisticVote(desiredVote(up.myVote, "upvote"))
        val down = none.withOptimisticVote(desiredVote(none.myVote, "downvote"))

        assertEquals(VoteState.Downvote, down.myVote)
        assertEquals(10, down.upvotes)
        assertEquals(5, down.downvotes)
    }

    @Test
    fun repeatedAbsoluteCommentStateIsIdempotent() {
        val comment = Comment(
            id = "comment-1",
            postId = "post-1",
            userId = "user-1",
            upvotes = 3,
            downvotes = 1,
            createdAt = "2026-07-11T12:00:00Z",
        )

        val once = comment.withOptimisticVote(VoteState.Downvote)
        val twice = once.withOptimisticVote(VoteState.Downvote)

        assertEquals(once, twice)
        assertEquals(3, twice.upvotes)
        assertEquals(2, twice.downvotes)
    }
}

