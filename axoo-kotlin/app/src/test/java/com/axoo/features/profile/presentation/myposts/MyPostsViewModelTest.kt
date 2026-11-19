package com.axoo.features.profile.presentation.myposts

import androidx.lifecycle.viewModelScope
import com.axoo.features.posts.data.PostChange
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.data.PostsPage
import com.axoo.features.posts.domain.Post
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyPostsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 8)
    private val repository = mockk<PostRepository> {
        every { this@mockk.changes } returns this@MyPostsViewModelTest.changes.asSharedFlow()
    }
    private fun post(id: String, body: String = id) = Post(
        id = id, userId = "me", universityId = "uniba", body = body, createdAt = "2026-10-16T10:00:00Z",
    )

    @Test fun `pagination deduplicates posts and prevents concurrent loads`() = runTest {
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(listOf(post("one")), "next")
        val pending = CompletableDeferred<PostsPage>()
        coEvery { repository.getMyPostsPage("next", 20) } coAnswers { pending.await() }
        val model = MyPostsViewModel(repository)
        advanceUntilIdle()
        model.loadMore()
        model.loadMore()
        runCurrent()
        coVerify(exactly = 1) { repository.getMyPostsPage("next", 20) }
        pending.complete(PostsPage(listOf(post("one"), post("two")), null))
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), model.state.value.posts.map { it.id })
        assertNull(model.state.value.nextCursor)
        assertFalse(model.state.value.loadingMore)
        model.viewModelScope.cancel()
    }

    @Test fun `failed pagination keeps posts and retries the failed cursor`() = runTest {
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(listOf(post("one")), "next")
        coEvery { repository.getMyPostsPage("next", 20) } throws IOException("offline")
        val model = MyPostsViewModel(repository)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertTrue(model.state.value.error)
        assertEquals(listOf(post("one")), model.state.value.posts)
        coEvery { repository.getMyPostsPage("next", 20) } returns PostsPage(listOf(post("two")), null)
        model.retry()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), model.state.value.posts.map { it.id })
        assertFalse(model.state.value.error)
        model.viewModelScope.cancel()
    }

    @Test fun `stale refresh does not resurrect deleted posts or overwrite edits and new posts`() = runTest {
        val original = listOf(post("one"), post("two"))
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(original, null)
        val model = MyPostsViewModel(repository)
        advanceUntilIdle()
        val pending = CompletableDeferred<PostsPage>()
        coEvery { repository.getMyPostsPage(null, 20) } coAnswers { pending.await() }
        model.refresh()
        runCurrent()
        changes.emit(PostChange.Deleted("one"))
        changes.emit(PostChange.Updated(post("two", "edited")))
        changes.emit(PostChange.Created(post("three")))
        runCurrent()
        pending.complete(PostsPage(original, null))
        advanceUntilIdle()
        assertEquals(listOf(post("three"), post("two", "edited")), model.state.value.posts)
        model.viewModelScope.cancel()
    }

    @Test fun `logout clears personal posts and next opening loads the new account`() = runTest {
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(listOf(post("old-account")), null)
        val model = MyPostsViewModel(repository)
        advanceUntilIdle()
        changes.emit(PostChange.Reset)
        runCurrent()
        assertTrue(model.state.value.posts.isEmpty())
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(listOf(post("new-account")), null)
        model.ensureLoaded()
        advanceUntilIdle()
        assertEquals("new-account", model.state.value.posts.single().id)
        model.viewModelScope.cancel()
    }

    @Test fun `failed vote rolls back counts without discarding an edit received during voting`() = runTest {
        coEvery { repository.getMyPostsPage(null, 20) } returns PostsPage(listOf(post("one")), null)
        val pending = CompletableDeferred<Unit>()
        coEvery { repository.votePost("one", 1) } coAnswers { pending.await(); throw IOException("offline") }
        val model = MyPostsViewModel(repository)
        advanceUntilIdle()
        model.vote(post("one"), "upvote")
        runCurrent()
        assertEquals(1, model.state.value.posts.single().upvotes)
        changes.emit(PostChange.Updated(model.state.value.posts.single().copy(body = "edited")))
        runCurrent()
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals("edited", model.state.value.posts.single().body)
        assertEquals(0, model.state.value.posts.single().upvotes)
        model.viewModelScope.cancel()
    }
}
