package com.axoo.features.notifications.presentation

import com.axoo.features.notifications.data.NotificationsApi
import androidx.lifecycle.viewModelScope
import com.axoo.features.posts.data.PostRepository
import com.axoo.core.session.SupabaseSessionStore
import com.axoo.features.notifications.data.*
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val api = mockk<NotificationsApi>()
    private val posts = PostRepository(mockk(), mockk(), mockk(relaxed = true))
    private val session = mockk<SupabaseSessionStore> { every { generation } returns 1L }
    private fun item(id: String) = AppNotification(id, "actor", "post", "comment-$id", "post_comment", "Giulia", "Giulia ha commentato il tuo post", "Testo", "2026-10-16T10:00:00Z")
    private fun prepare(): NotificationsRepository {
        coEvery { api.getNotifications(null) } returns NotificationsPage(listOf(item("3")), "2", "3", 3)
        coEvery { api.getNotificationsUnreadCount() } returns NotificationsCount(3)
        coEvery { api.markNotificationsSeen(any()) } returns NotificationsCount(1)
        return NotificationsRepository(api, session)
    }

    @Test fun `notifications are acknowledged only after a successful visible display`() = runTest {
        val repository = prepare()
        val model = NotificationsViewModel(repository, posts)
        model.acknowledge()
        model.open()
        advanceUntilIdle()
        assertEquals(3, model.unread.value)
        coVerify(exactly = 0) { api.markNotificationsSeen(any()) }
        model.acknowledge()
        advanceUntilIdle()
        coVerify(exactly = 1) { api.markNotificationsSeen(NotificationsSeenRequest("3")) }
        assertEquals(1, model.unread.value) // A new arrival remains unread.
        model.acknowledge()
        advanceUntilIdle()
        coVerify(exactly = 1) { api.markNotificationsSeen(any()) }
        model.viewModelScope.cancel()
    }

    @Test fun `failed open never marks notifications seen`() = runTest {
        val model = NotificationsViewModel(prepare(), posts)
        coEvery { api.getNotifications(null) } throws IOException("offline")
        model.open()
        advanceUntilIdle()
        model.acknowledge()
        advanceUntilIdle()
        assertTrue(model.state.value.error)
        coVerify(exactly = 0) { api.markNotificationsSeen(any()) }
        model.viewModelScope.cancel()
    }

    @Test fun `pagination retains items retries the cursor and removes duplicates`() = runTest {
        val model = NotificationsViewModel(prepare(), posts)
        model.open()
        advanceUntilIdle()
        coEvery { api.getNotifications("2") } throws IOException("offline")
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("3"), model.state.value.items.map { it.id })
        assertTrue(model.state.value.failedLoadMore)
        coEvery { api.getNotifications("2") } returns NotificationsPage(listOf(item("3"), item("2")), "", "2", 3)
        model.retry()
        advanceUntilIdle()
        assertEquals(listOf("3", "2"), model.state.value.items.map { it.id })
        assertNull(model.state.value.cursor)
        assertEquals("3", model.state.value.seenThrough)
        model.viewModelScope.cancel()
    }

    @Test fun `late unread count cannot undo an acknowledgement`() = runTest {
        val repository = prepare()
        val response = CompletableDeferred<NotificationsCount>()
        coEvery { api.getNotificationsUnreadCount() } coAnswers { response.await() }
        val loading = async { repository.refreshUnread() }
        runCurrent()
        repository.seen("3")
        assertEquals(1, repository.unread.value)
        response.complete(NotificationsCount(9))
        loading.await()
        assertEquals(1, repository.unread.value)
    }

    @Test fun `new session clears the previous account badge`() = runTest {
        val repository = prepare()
        repository.refreshUnread()
        assertEquals(3, repository.unread.value)
        every { session.generation } returns 2L
        repository.prepareSession()
        assertEquals(0, repository.unread.value)
    }

    @Test fun `failed push refresh still invalidates the list without failing delivery`() = runTest {
        val repository = prepare()
        coEvery { api.getNotificationsUnreadCount() } throws IOException("offline")
        val changed = async { repository.changes.first() }
        runCurrent()
        repository.received()
        changed.await()
        assertEquals(0, repository.unread.value)
    }
    @Test fun `blocking an author removes cached notifications and reloads when reopened`() = runTest {
        val model = NotificationsViewModel(prepare(), posts)
        model.open()
        advanceUntilIdle()
        model.close()
        coEvery { api.getNotificationsUnreadCount() } returns NotificationsCount(0)
        coEvery { api.getNotifications(null) } returns NotificationsPage(emptyList(), "", "", 0)
        posts.setAuthorBlocked("actor", true)
        advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        model.open()
        advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertEquals(0, model.unread.value)
        model.viewModelScope.cancel()
    }

}
