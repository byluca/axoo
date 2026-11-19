package com.axoo.features.auth.data

import com.axoo.core.network.*
import com.axoo.core.session.*
import com.axoo.features.auth.data.dto.*
import com.axoo.features.moderation.data.dto.*
import com.axoo.features.posts.data.dto.*
import com.axoo.features.profile.data.ProfileRepository
import com.axoo.core.push.PushRegistration
import com.axoo.features.profile.data.dto.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryLifecycleTest {
    private val api = mockk<AuthApi>(relaxed = true)
    private val session = mockk<SupabaseSessionStore>(relaxed = true)
    private val profiles = mockk<ProfileRepository>(relaxed = true)
    private val supabase = mockk<SupabaseAuthApi>()
    private val push = mockk<PushRegistration>(relaxed = true)
    private fun repository() = AuthRepository(api, supabase, session, profiles, push)

    @Test fun `logout stops push callbacks and cleans up with captured old credentials`() = runTest {
        coEvery { session.clear() } returns "old-access"
        repository().logout()
        coVerifyOrder {
            push.stop()
            session.invalidate()
            session.clear()
            profiles.clearCache()
            push.unregister("old-access")
        }
    }

    @Test fun `login clears previous profile before saving the new session`() = runTest {
        val response = SupabaseSessionResponse("new-access", "new-refresh", 3600)
        coEvery { supabase.verifyEmailOtp(any(), any()) } returns response
        repository().verifyOtp("student@example.com", "123456")

        coVerifyOrder {
            profiles.clearCache()
            session.save(response, 0)
            api.createSession()
        }
    }

    @Test fun `logout clears profile even if removing credentials fails`() = runTest {
        coEvery { session.clear() } throws IOException("storage unavailable")
        runCatching { repository().logout() }

        coVerify(exactly = 1) { profiles.clearCache() }
    }

    @Test fun `failed cleanup blocks new login until retry succeeds`() = runTest {
        coEvery { session.clear() } throws IOException("disk unavailable")
        val repo = repository()
        assertTrue(runCatching { repo.logout() }.isFailure)
        assertTrue(repo.logoutPending)
        assertTrue(runCatching { repo.verifyOtp("student@example.com", "123456") }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        coVerify(exactly = 0) { supabase.verifyEmailOtp(any(), any()) }

        coEvery { session.clear() } returns null
        repo.logout()
        assertFalse(repo.logoutPending)
        coVerify(exactly = 2) { profiles.clearCache() }
    }
}
