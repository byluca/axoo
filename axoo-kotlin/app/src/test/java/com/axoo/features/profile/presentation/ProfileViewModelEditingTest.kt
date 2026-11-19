package com.axoo.features.profile.presentation

import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.domain.User
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelEditingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val repository = mockk<ProfileRepository>()
    private val currentUser = User(id = "me", nickname = "student", universityId = "campus", createdAt = "2026-08-16T00:00:00Z")

    private fun createModel(): ProfileViewModel {
        every { repository.getCachedUser() } returns currentUser
        coEvery { repository.getMe() } returns currentUser
        return ProfileViewModel(repository)
    }

    @Test
    fun `nickname validation rejects invalid drafts without requesting availability`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        assertEquals(NicknameStatus.Current, model.nickname.value.status)
        assertFalse(model.nickname.value.canSave)

        for (value in listOf("", "ab", "a".repeat(25), "Student", "has space", "città")) {
            model.changeNickname(value)
            advanceUntilIdle()
            assertEquals(NicknameStatus.Invalid, model.nickname.value.status)
            assertFalse(model.nickname.value.canSave)
        }
        coVerify(exactly = 0) { repository.isNicknameAvailable(any()) }
        coVerify(exactly = 0) { repository.updateNickname(any()) }
    }

    @Test
    fun `availability waits for the last draft and trims before requesting`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        coEvery { repository.isNicknameAvailable("lastnick") } returns true
        model.changeNickname("firstnick")
        advanceTimeBy(200)
        model.changeNickname(" lastnick ")
        advanceTimeBy(399)
        runCurrent()
        assertEquals(NicknameStatus.Checking, model.nickname.value.status)
        coVerify(exactly = 0) { repository.isNicknameAvailable(any()) }
        advanceTimeBy(1)
        runCurrent()
        assertEquals("lastnick", model.nickname.value.value)
        assertTrue(model.nickname.value.canSave)
        coVerify(exactly = 1) { repository.isNicknameAvailable("lastnick") }
    }

    @Test
    fun `late availability failure cannot replace a new editing session`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        val old = CompletableDeferred<Boolean>()
        coEvery { repository.isNicknameAvailable("oldnick") } coAnswers {
            withContext(NonCancellable) { old.await() }
        }
        coEvery { repository.isNicknameAvailable("newnick") } returns true
        model.changeNickname("oldnick")
        advanceTimeBy(400)
        runCurrent()
        model.stopNicknameEditing()
        model.beginNicknameEditing()
        model.changeNickname("newnick")
        advanceUntilIdle()
        old.completeExceptionally(IOException("late failure"))
        advanceUntilIdle()
        assertEquals("newnick", model.nickname.value.value)
        assertTrue(model.nickname.value.canSave)
    }

    @Test
    fun `saving freezes the draft ignores duplicates and updates the shared profile`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        coEvery { repository.isNicknameAvailable("newnick") } returns true
        val saving = CompletableDeferred<User>()
        coEvery { repository.updateNickname("newnick") } coAnswers { saving.await() }
        model.changeNickname("newnick")
        advanceUntilIdle()
        model.saveNickname()
        model.saveNickname()
        model.changeNickname("another")
        runCurrent()
        assertEquals(NicknameStatus.Saving, model.nickname.value.status)
        assertEquals("newnick", model.nickname.value.value)
        coVerify(exactly = 1) { repository.updateNickname("newnick") }
        saving.complete(currentUser.copy(nickname = "newnick"))
        advanceUntilIdle()
        assertEquals(NicknameStatus.Saved, model.nickname.value.status)
        assertEquals("newnick", model.me.value.getOrNull()?.nickname)
        assertFalse(model.nickname.value.canSave)
    }

    @Test
    fun `failed nickname save preserves the draft for a retry`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        coEvery { repository.isNicknameAvailable("newnick") } returns true
        coEvery { repository.updateNickname("newnick") } throws IOException("offline")
        model.changeNickname("newnick")
        advanceUntilIdle()
        model.saveNickname()
        advanceUntilIdle()
        assertEquals("newnick", model.nickname.value.value)
        assertEquals(NicknameStatus.Invalid, model.nickname.value.status)
        assertEquals("student", model.me.value.getOrNull()?.nickname)
        coEvery { repository.updateNickname("newnick") } returns currentUser.copy(nickname = "newnick")
        model.changeNickname("newnick")
        advanceUntilIdle()
        model.saveNickname()
        advanceUntilIdle()
        assertEquals(NicknameStatus.Saved, model.nickname.value.status)
    }

    @Test
    fun `profile arriving after editing starts preserves a typed nickname`() = runTest {
        every { repository.getCachedUser() } returns null
        val profile = CompletableDeferred<User>()
        coEvery { repository.getMe() } coAnswers { profile.await() }
        coEvery { repository.isNicknameAvailable("newnick") } returns true
        val model = ProfileViewModel(repository)
        model.beginNicknameEditing()
        model.changeNickname("newnick")
        advanceUntilIdle()
        assertFalse(model.nickname.value.canSave)
        profile.complete(currentUser)
        advanceUntilIdle()
        assertEquals("newnick", model.nickname.value.value)
        assertEquals("student", model.nickname.value.currentNickname)
        assertTrue(model.nickname.value.canSave)
    }

    @Test
    fun `leaving during saving prevents a late success from closing a new editor`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        model.beginNicknameEditing()
        coEvery { repository.isNicknameAvailable("newnick") } returns true
        val saving = CompletableDeferred<User>()
        coEvery { repository.updateNickname("newnick") } coAnswers {
            withContext(NonCancellable) { saving.await() }
        }
        model.changeNickname("newnick")
        advanceUntilIdle()
        model.saveNickname()
        runCurrent()
        model.stopNicknameEditing()
        model.beginNicknameEditing()
        saving.complete(currentUser.copy(nickname = "newnick"))
        advanceUntilIdle()
        assertEquals(NicknameStatus.Current, model.nickname.value.status)
        assertEquals("student", model.nickname.value.value)
        assertEquals(currentUser, model.me.value.getOrNull())
    }

    @Test
    fun `blocked users loading ignores duplicate requests and supports retry`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        val loading = CompletableDeferred<List<User>>()
        coEvery { repository.blockedUsers() } coAnswers { loading.await() }
        model.loadBlockedUsers()
        model.loadBlockedUsers()
        runCurrent()
        coVerify(exactly = 1) { repository.blockedUsers() }
        loading.completeExceptionally(IOException("offline"))
        advanceUntilIdle()
        assertTrue(model.blockedUsers.value.users is com.axoo.ui.state.UiState.Error)
        coEvery { repository.blockedUsers() } returns listOf(currentUser)
        model.loadBlockedUsers()
        advanceUntilIdle()
        assertEquals(listOf(currentUser), model.blockedUsers.value.users.getOrNull())
    }

    @Test
    fun `failed unblock retains users and retry removes only the selected user`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        val first = currentUser.copy(id = "first")
        val second = currentUser.copy(id = "second")
        coEvery { repository.blockedUsers() } returns listOf(first, second)
        coEvery { repository.unblockUser("first") } throws IOException("offline")
        model.loadBlockedUsers()
        advanceUntilIdle()
        model.unblockUser("first")
        advanceUntilIdle()
        assertTrue(model.blockedUsers.value.operationFailed)
        assertEquals(listOf(first, second), model.blockedUsers.value.users.getOrNull())
        val unblocking = CompletableDeferred<Unit>()
        coEvery { repository.unblockUser("first") } coAnswers { unblocking.await() }
        model.unblockUser("first")
        model.unblockUser("first")
        model.unblockUser("second")
        runCurrent()
        assertEquals("first", model.blockedUsers.value.unblockingUserId)
        coVerify(exactly = 2) { repository.unblockUser("first") }
        coVerify(exactly = 0) { repository.unblockUser("second") }
        unblocking.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(second), model.blockedUsers.value.users.getOrNull())
        assertFalse(model.blockedUsers.value.operationFailed)
        assertEquals(null, model.blockedUsers.value.unblockingUserId)
    }

    @Test
    fun `cancelled blocked load cannot replace the list after reopening`() = runTest {
        val model = createModel()
        advanceUntilIdle()
        val loading = CompletableDeferred<List<User>>()
        coEvery { repository.blockedUsers() } coAnswers {
            withContext(NonCancellable) { loading.await() }
        }
        model.loadBlockedUsers()
        runCurrent()
        model.stopBlockedUsers()
        val fresh = currentUser.copy(id = "fresh")
        coEvery { repository.blockedUsers() } returns listOf(fresh)
        model.loadBlockedUsers()
        advanceUntilIdle()
        loading.complete(listOf(currentUser))
        advanceUntilIdle()
        assertEquals(listOf(fresh), model.blockedUsers.value.users.getOrNull())
    }
}
