package com.axoo.features.auth.presentation

import com.axoo.features.auth.data.AuthRepository
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restricted account deletion validates confirmation and ignores duplicate submission`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val deletion = CompletableDeferred<Unit>()
        coEvery { repository.deleteAccount() } coAnswers { deletion.await() }
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        var failure: AuthController.DeletionFailure? = null
        controller.deleteRestrictedAccount("wrong") { failure = it }
        assertEquals(AuthController.DeletionFailure.InvalidConfirmation, failure)
        coVerify(exactly = 0) { repository.deleteAccount() }
        controller.deleteRestrictedAccount(" ELIMINA ") { failure = it }
        controller.deleteRestrictedAccount("ELIMINA") { failure = it }
        runCurrent()
        controller.refreshSessionStatus()
        coVerify(exactly = 0) { repository.bootstrapSession() }
        deletion.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.deleteAccount() }
        coVerify(exactly = 1) { repository.logout() }
        assertEquals("intro", ui.destination)
    }

    @Test
    fun `failed logout stays blocked until a successful retry`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(status = "active")
        coEvery { repository.logout() } throws IOException("cache unavailable")
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("account", ui.destination)

        controller.logout()
        advanceUntilIdle()

        assertEquals("logout-failed", ui.destination)
        controller.refreshSessionStatus()
        controller.onAuthenticated(SessionBootstrapResponse(status = "active"))
        advanceUntilIdle()
        assertEquals("logout-failed", ui.destination)
        coVerify(exactly = 1) { repository.bootstrapSession() }

        coEvery { repository.logout() } returns null
        controller.logout()
        assertEquals("logging-out", ui.destination)
        advanceUntilIdle()
        assertEquals("intro", ui.destination)
    }

    @Test
    fun `existing suspended session exposes the restriction`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(
            status = "suspended",
            reason = "harassment",
            suspendedUntil = "2026-08-31T12:30:00Z",
        )

        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()

        assertEquals(
            SessionBootstrapResponse(
                status = "suspended",
                reason = "harassment",
                suspendedUntil = "2026-08-31T12:30:00Z",
            ),
            ui.account,
        )
    }

    @Test
    fun `logout immediately blocks refresh and repeated taps run cleanup once`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(status = "active")
        val cleanup = CompletableDeferred<String?>()
        coEvery { repository.logout() } coAnswers { cleanup.await() }
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()

        controller.logout()
        assertEquals("logging-out", ui.destination)
        controller.logout()
        controller.refreshSessionStatus()
        runCurrent()
        coVerify(exactly = 1) { repository.logout() }
        coVerify(exactly = 1) { repository.bootstrapSession() }

        cleanup.complete(null)
        advanceUntilIdle()
        controller.logout()
        controller.onAuthenticated(SessionBootstrapResponse(status = "active"))
        advanceUntilIdle()
        assertEquals("intro", ui.destination)
        coVerify(exactly = 1) { repository.logout() }
    }

    @Test
    fun `late bootstrap response cannot restore session after logout`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.logout() } returns null
        val response = CompletableDeferred<SessionBootstrapResponse>()
        coEvery { repository.bootstrapSession() } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        runCurrent()
        controller.logout()
        runCurrent()
        assertEquals("intro", ui.destination)

        response.complete(SessionBootstrapResponse(status = "active"))
        advanceUntilIdle()
        assertEquals("intro", ui.destination)
    }

    @Test
    fun `new controller cannot bypass an unfinished logout`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        every { repository.logoutPending } returns true
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("logout-failed", ui.destination)
        coVerify(exactly = 0) { repository.isLoggedIn() }
    }

    @Test
    fun `login after intro restores logout and resume actions`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns false
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("intro", ui.destination)

        controller.onAuthenticated(SessionBootstrapResponse(status = "active"))
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(status = "active")
        controller.refreshSessionStatus()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.bootstrapSession() }
        controller.logout()
        advanceUntilIdle()
        assertEquals("intro", ui.destination)
        coVerify(exactly = 1) { repository.logout() }
    }

    @Test
    fun `failed cold bootstrap offers recovery without granting access`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } throws IOException("offline")
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("recovery", ui.destination)
        assertEquals(null, ui.account)

        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(status = "active")
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("account", ui.destination)
    }

    @Test
    fun `unknown account status never grants access`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        coEvery { repository.bootstrapSession() } returns SessionBootstrapResponse(status = "unknown")
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals("recovery", ui.destination)
        assertEquals(null, ui.account)
    }

    @Test
    fun `failed background bootstrap keeps the existing restriction`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.isLoggedIn() } returns true
        val suspended = SessionBootstrapResponse(status = "suspended", reason = "spam")
        coEvery { repository.bootstrapSession() } returns suspended
        val ui = AuthUI()
        val controller = ui.controller(repository, this)
        controller.refreshSessionStatus()
        advanceUntilIdle()
        coEvery { repository.bootstrapSession() } throws IOException("offline")
        controller.refreshSessionStatus()
        advanceUntilIdle()
        assertEquals(suspended, ui.account)
        assertEquals("account", ui.destination)
    }

    @Test
    fun `cancelled activity scope prevents late logout navigation`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext.minusKey(kotlinx.coroutines.Job))
        val cleanup = CompletableDeferred<String?>()
        coEvery { repository.logout() } coAnswers {
            withContext(NonCancellable) { cleanup.await() }
        }
        val ui = AuthUI()
        val controller = ui.controller(repository, scope)
        controller.logout()
        runCurrent()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        cleanup.complete(null)
        advanceUntilIdle()
        assertEquals("logging-out", ui.destination)
    }

}

private class AuthUI {
    var destination = "checking"
    var account: SessionBootstrapResponse? = null

    fun controller(repository: AuthRepository, scope: CoroutineScope) = AuthController(
        operations = AuthOperations(repository),
        scope = scope,
        onChecking = { destination = "checking" },
        onLoggedOut = { destination = "intro" },
        onAccount = { account = it; destination = "account" },
        onLogout = { destination = if (it) "logout-failed" else "logging-out" },
        onRestoreFailed = { destination = "recovery" },
    )
}
