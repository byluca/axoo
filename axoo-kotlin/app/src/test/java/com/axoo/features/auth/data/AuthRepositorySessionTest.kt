package com.axoo.features.auth.data

import com.axoo.core.session.SupabaseAuthApi
import com.axoo.core.session.SupabaseSessionResponse

import com.axoo.core.session.SupabaseSessionStore
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.testing.MemoryPreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthRepositorySessionTest {
    private val api = mockk<AuthApi>(relaxed = true)
    private val supabase = mockk<SupabaseAuthApi>()
    private val store = SupabaseSessionStore(MemoryPreferencesStore(), supabase)
    private val repo = AuthRepository(api, supabase, store, mockk(relaxed = true), mockk(relaxed = true))

    @Test fun `alpha login stores a regular persistent session without an otp`() = runTest {
        coEvery { api.signInAlpha(any()) } returns SupabaseSessionResponse("alpha-access", "alpha-refresh", 3600)
        coEvery { api.createSession() } returns SessionBootstrapResponse(status = "active")
        val result = repo.signInAlpha("alpha@example.com", "shared-test-password")
        assertEquals("active", result.status)
        assertEquals("alpha-access", store.accessToken())
        assertTrue(store.hasSession())
        coVerify(exactly = 0) { supabase.sendEmailOtp(any(), any()) }
        coVerify(exactly = 0) { supabase.verifyEmailOtp(any(), any()) }
    }

    @Test fun `alpha response arriving after logout cannot restore credentials`() = runTest {
        val response = CompletableDeferred<SupabaseSessionResponse>()
        coEvery { api.signInAlpha(any()) } coAnswers { response.await() }
        val login = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { repo.signInAlpha("alpha@example.com", "shared-test-password") }
        }
        repo.logout()
        response.complete(SupabaseSessionResponse("old-alpha", "old-refresh", 3600))
        assertTrue(login.await().exceptionOrNull() is CancellationException)
        assertFalse(store.hasSession())
        coVerify(exactly = 0) { api.createSession() }
    }

    @Test fun `OTP response arriving after logout cannot restore credentials`() = runTest {
        val response = CompletableDeferred<SupabaseSessionResponse>()
        coEvery { supabase.verifyEmailOtp(any(), any()) } coAnswers { response.await() }
        val login = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { repo.verifyOtp("student@example.com", "123456") }
        }
        repo.logout()
        response.complete(SupabaseSessionResponse("old-access", "old-refresh", 3600))

        assertTrue(login.await().exceptionOrNull() is CancellationException)
        assertFalse(store.hasSession())
        coVerify(exactly = 0) { api.createSession() }
    }

    @Test fun `late bootstrap success for logged out account is rejected`() = runTest {
        store.save(SupabaseSessionResponse("a", "refresh-a", 3600))
        val response = CompletableDeferred<SessionBootstrapResponse>()
        coEvery { api.createSession() } coAnswers { response.await() }
        val bootstrap = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.bootstrapSession() } }
        repo.logout()
        response.complete(SessionBootstrapResponse(status = "active"))
        assertTrue(bootstrap.await().exceptionOrNull() is CancellationException)
        assertFalse(store.hasSession())
    }

    @Test fun `late bootstrap 401 for A cannot clear newly logged in B`() = runTest {
        store.save(SupabaseSessionResponse("a", "refresh-a", 3600))
        val response = CompletableDeferred<SessionBootstrapResponse>()
        coEvery { api.createSession() } coAnswers { response.await() }
        val bootstrap = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.bootstrapSession() } }
        repo.logout()
        coEvery { supabase.verifyEmailOtp(any(), any()) } returns SupabaseSessionResponse("b", "refresh-b", 3600)
        coEvery { api.createSession() } returns SessionBootstrapResponse(status = "active")
        repo.verifyOtp("b@example.com", "123456")
        response.completeExceptionally(HttpException(Response.error<Any>(401, "{}".toResponseBody())))
        assertTrue(bootstrap.await().isFailure)
        assertEquals("b", store.accessToken())
    }

    @Test fun `failed login bootstrap for A cannot clear newly logged in B`() = runTest {
        val response = CompletableDeferred<SessionBootstrapResponse>()
        coEvery { supabase.verifyEmailOtp(any(), any()) } returns SupabaseSessionResponse("a", "refresh-a", 3600)
        coEvery { api.createSession() } coAnswers { response.await() }
        val login = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.verifyOtp("a@example.com", "123456") } }
        repo.logout()
        coEvery { supabase.verifyEmailOtp(any(), any()) } returns SupabaseSessionResponse("b", "refresh-b", 3600)
        coEvery { api.createSession() } returns SessionBootstrapResponse(status = "active")
        repo.verifyOtp("b@example.com", "123456")
        response.completeExceptionally(java.io.IOException("late network failure"))
        assertTrue(login.await().isFailure)
        assertEquals("b", store.accessToken())
    }
}
