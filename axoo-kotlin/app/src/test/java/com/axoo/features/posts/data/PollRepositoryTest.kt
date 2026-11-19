package com.axoo.features.posts.data

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.data.dto.PollVoteRequest
import com.axoo.features.posts.data.dto.PollVoteResponse
import com.axoo.features.posts.data.dto.PostResponse
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.PostPoll
import com.axoo.features.posts.domain.PostPollOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PollRepositoryTest {
    private val api = mockk<PostsApi>()
    private val repo = PostRepository(mockk(), api, mockk(relaxed = true))
    private val poll = PostPoll(options = listOf(PostPollOption("a", "A"), PostPollOption("b", "B")))
    private val saved = poll.withOptimisticVote("a").copy(isSending = false)

    @Test fun `rapid taps send one request and publish pending and final state`() = runTest {
        val events = mutableListOf<PostChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { events.add(it) } }
        val response = CompletableDeferred<PollVoteResponse>()
        coEvery { api.votePoll("post", any()) } coAnswers { response.await() }
        val request = launch { repo.votePoll("post", poll, "a") }
        runCurrent()
        repo.votePoll("post", poll, "b")
        assertTrue((events.single() as PostChange.PollChanged).poll.isSending)
        response.complete(PollVoteResponse(saved))
        request.join()
        assertEquals(saved, (events.last() as PostChange.PollChanged).poll)
        coVerify(exactly = 1) { api.votePoll("post", PollVoteRequest("a")) }
        repo.votePoll("post", poll, "b")
        coVerify(exactly = 1) { api.votePoll(any(), any()) }
    }

    @Test fun `failed vote restores the poll on every screen and permits retry`() = runTest {
        val events = mutableListOf<PostChange.PollChanged>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.changes.collect { if (it is PostChange.PollChanged) events.add(it) }
        }
        coEvery { api.votePoll("post", any()) } throws IOException("offline")
        coEvery { api.getPost("post") } throws IOException("offline")
        try { repo.votePoll("post", poll, "a"); fail("expected failure") } catch (_: IOException) { }
        assertEquals(poll, events.last().poll)
        coEvery { api.votePoll("post", any()) } returns PollVoteResponse(saved)
        repo.votePoll("post", poll, "a")
        assertEquals(saved, events.last().poll)
    }

    @Test fun `a committed vote with a lost response is reconciled instead of rolled back`() = runTest {
        val events = mutableListOf<PostChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { events.add(it) } }
        coEvery { api.votePoll("post", any()) } throws IOException("response lost")
        coEvery { api.getPost("post") } returns PostResponse(post(saved))
        repo.votePoll("post", poll, "a")
        assertEquals(saved, (events.last() as PostChange.PollChanged).poll)
    }

    @Test fun `cancellation during reconciliation restores pending state and releases the lock`() = runTest {
        val events = mutableListOf<PostChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { events.add(it) } }
        coEvery { api.votePoll("post", any()) } throws IOException("offline")
        coEvery { api.getPost("post") } coAnswers { CompletableDeferred<PostResponse>().await() }
        val request = launch { repo.votePoll("post", poll, "a") }
        runCurrent()
        request.cancel(); request.join()
        assertEquals(poll, (events.last() as PostChange.PollChanged).poll)
        coEvery { api.votePoll("post", any()) } returns PollVoteResponse(saved)
        repo.votePoll("post", poll, "a")
        assertEquals(saved, (events.last() as PostChange.PollChanged).poll)
    }

    @Test fun `option emoji offsets are converted from server code points to Android UTF16`() = runTest {
        val option = PostPollOption("a", "😀 🔥", entities = listOf(MessageEntity(offset = 2, length = 1, customEmojiId = 7)))
        val result = saved.copy(options = listOf(option, poll.options[1]))
        coEvery { api.getPost("post") } returns PostResponse(post(result))
        val loaded = repo.getPost("post").poll!!.options[0]
        assertEquals(3, loaded.entities.single().offset)
        assertEquals(2, loaded.entities.single().length)
    }

    private fun post(poll: PostPoll) = Post("post", "owner", universityId = "campus", createdAt = "2026-10-25T00:00:00Z", poll = poll)
}
