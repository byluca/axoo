package com.axoo.features.profile.presentation

import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.domain.User
import com.axoo.testing.MainDispatcherRule
import com.axoo.ui.state.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ProfileRepository>()

    @Test
    fun `invalid deletion confirmation never reaches the repository`() = runTest {
        val repository = mockk<ProfileRepository>(relaxed = true)
        val vm = ProfileViewModel(repository)
        var error: Throwable? = null
        vm.deleteAccount(onSuccess = {}, onFailure = { error = it }, confirmation = "wrong")
        advanceUntilIdle()
        assertEquals(true, error is AccountDeletionConfirmationError)
        coVerify(exactly = 0) { repository.deleteAccount() }
    }

    @Test
    fun `background reload retains profile and ignores duplicate requests`() = runTest {
        prepareProfile()
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()
        val previous = viewModel.me.value
        val pending = CompletableDeferred<User>()
        coEvery { repository.getMe() } coAnswers { pending.await() }

        viewModel.refresh()
        viewModel.refresh()
        runCurrent()
        assertEquals(previous, viewModel.me.value)
        coVerify(exactly = 1) { repository.getMe() }

        pending.complete((previous as UiState.Success).data)
        advanceUntilIdle()
        assertEquals(previous, viewModel.me.value)
    }

    @Test
    fun `failed background reload does not replace existing profile with loading or error`() = runTest {
        prepareProfile()
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()
        val previous = viewModel.me.value
        coEvery { repository.getMe() } throws IllegalStateException("offline")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(previous, viewModel.me.value)
    }

    @Test
    fun `account deletion ignores duplicate requests while one is running`() = runTest {
        prepareProfile()
        val deletion = CompletableDeferred<Unit>()
        coEvery { repository.deleteAccount() } coAnswers { deletion.await() }
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteAccount(onSuccess = {})
        viewModel.deleteAccount(onSuccess = {})
        runCurrent()

        assertTrue(viewModel.isDeletingAccount.value)
        coVerify(exactly = 1) { repository.deleteAccount() }

        deletion.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.isDeletingAccount.value)
    }

    private fun prepareProfile() {
        val user = User(
            id = "user-id",
            nickname = "Utente",
            universityId = "university-id",
            createdAt = "2026-08-16T00:00:00Z",
        )
        every { repository.getCachedUser() } returns user
        coEvery { repository.getMe() } returns user
    }
}
