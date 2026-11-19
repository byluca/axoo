package com.axoo.features.posts.domain

import org.junit.Assert.*
import org.junit.Test

class PostPollTest {
    @Test fun `percentages sum to one hundred with deterministic rounding`() {
        val poll = PostPoll(options = listOf(option("a", 1), option("b", 1), option("c", 1)), totalVotes = 3)
        assertEquals(listOf(34, 33, 33), poll.percentages())
        assertEquals(listOf(17, 33, 50), poll.copy(options = listOf(option("a", 1), option("b", 2), option("c", 3))).percentages())
        assertEquals(listOf(0, 0), PostPoll(options = listOf(option("a", 0), option("b", 0))).percentages())
    }

    @Test fun `a pending or final vote cannot change or increment twice`() {
        val original = PostPoll(options = listOf(option("a", 0), option("b", 0)))
        val selected = original.withOptimisticVote("a")
        assertEquals("a", selected.myOptionId)
        assertTrue(selected.isSending)
        assertEquals(1, selected.totalVotes)
        assertEquals(selected, selected.withOptimisticVote("b"))
        assertEquals(selected, selected.withOptimisticVote("a"))
        assertEquals(original, original.withOptimisticVote("unknown"))
        val final = selected.copy(isSending = false)
        assertEquals(final, final.withOptimisticVote("b"))
    }

    @Test fun `option validation counts unicode code points and enforces two to four options`() {
        val valid = listOf(PollOptionInput("😀".repeat(100)), PollOptionInput("B"))
        assertTrue(isPollInputValid(valid))
        assertFalse(isPollInputValid(valid.take(1)))
        assertFalse(isPollInputValid(List(5) { PollOptionInput("A") }))
        assertFalse(isPollInputValid(listOf(PollOptionInput("  "), valid[1])))
        assertFalse(isPollInputValid(listOf(PollOptionInput("😀".repeat(101)), valid[1])))
    }

    private fun option(id: String, votes: Int) = PostPollOption(id, id, votes = votes)
}
