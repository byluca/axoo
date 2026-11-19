package com.axoo.core.push

import android.util.Log
import com.axoo.core.session.SupabaseSessionStore
import com.axoo.testing.MemoryPreferencesStore
import com.axoo.testing.MainDispatcherRule
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.*
import java.io.IOException
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val api = mockk<PushApi>(relaxed = true)
    private val session = mockk<SupabaseSessionStore>(relaxed = true)
    private val messaging = mockk<FirebaseMessaging>(relaxed = true)
    private val installations = mockk<FirebaseInstallations>()
    private val notifications = mockk<PushNotifications>(relaxed = true)
    private val preferences = MemoryPreferencesStore()
    private val registration = PushRegistration(preferences, api, session, Provider { messaging }, Provider { installations }, notifications)
    private var generation = 1L

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { session.generation } answers { generation }
        coEvery { session.currentUserId() } returns "account-A"
        coEvery { session.accessToken() } returns "access-A"
        every { notifications.isEnabled() } returns true
        every { messaging.register() } returns Tasks.forResult(null)
        every { messaging.unregister() } returns Tasks.forResult(null)
        every { installations.id } returns Tasks.forResult("fid-1")
    }

    @After fun tearDown() { unmockkStatic(Log::class) }

    @Test fun `registration refresh reuses installation id and current account credentials`() = runTest {
        val ids = mutableListOf<String>()
        val requests = mutableListOf<PushRegistrationRequest>()
        coEvery { api.registerPushInstallation(capture(ids), capture(requests), "Bearer access-A") } just Runs
        registration.activate()
        registration.synchronize("fid-2")

        assertEquals(2, ids.size)
        assertEquals(ids[0], ids[1])
        assertEquals(listOf("fid-1", "fid-2"), requests.map { it.registration })
        verify(exactly = 1) { messaging.register() }
    }

    @Test fun `failed upload retries on the next authenticated activation`() = runTest {
        var attempts = 0
        coEvery { api.registerPushInstallation(any(), any(), any()) } coAnswers {
            if (++attempts == 1) throw IOException("offline")
        }
        registration.activate()
        registration.activate()
        assertEquals(2, attempts)
    }

    @Test fun `missing permission does not register with Firebase or backend`() = runTest {
        every { notifications.isEnabled() } returns false
        registration.activate()
        verify(exactly = 0) { messaging.register() }
        coVerify(exactly = 0) { api.registerPushInstallation(any(), any(), any()) }
    }

    @Test fun `logout while Firebase registers cannot upload credentials later`() = runTest {
        val pending = TaskCompletionSource<Void>()
        every { messaging.register() } returns pending.task
        val activation = async(start = CoroutineStart.UNDISPATCHED) { registration.activate() }
        registration.stop()
        generation++
        pending.setResult(null)
        activation.await()

        coVerify(exactly = 0) { api.registerPushInstallation(any(), any(), any()) }
        assertFalse(registration.isCurrentRecipient("account-A"))
    }

    @Test fun `logout waits for in flight binding and removes it with the old token`() = runTest {
        val upload = CompletableDeferred<Unit>()
        coEvery { api.registerPushInstallation(any(), any(), any()) } coAnswers { upload.await() }
        val activation = async(start = CoroutineStart.UNDISPATCHED) { registration.activate() }
        registration.stop()
        generation++
        coEvery { session.accessToken() } returns "access-B"
        val logout = async(start = CoroutineStart.UNDISPATCHED) { registration.unregister("access-A") }
        upload.complete(Unit)
        activation.await()
        logout.await()
        advanceUntilIdle()
        registration.synchronize("late-fid")

        coVerify(exactly = 1) { api.registerPushInstallation(any(), any(), "Bearer access-A") }
        coVerify(exactly = 1) { api.removePushInstallation(any(), PushRegistrationRequest("fid-1"), "Bearer access-A") }
        coVerify(exactly = 0) { api.removePushInstallation(any(), any(), "Bearer access-B") }
        verify(exactly = 1) { notifications.clear() }
    }

    @Test fun `offline cleanup invalidates Firebase even when backend deletion fails`() = runTest {
        registration.activate()
        coEvery { api.removePushInstallation(any(), any(), any()) } throws IOException("offline")
        registration.stop()
        registration.unregister("access-A")
        verify(exactly = 1) { messaging.unregister() }
    }

    @Test fun `logout returns during SDK cleanup and the next registration waits`() = runTest {
        registration.activate()
        val pending = TaskCompletionSource<Void>()
        every { messaging.unregister() } returns pending.task
        registration.stop()
        generation++

        registration.unregister("access-A")
        assertFalse(pending.task.isComplete)
        registration.clearSignedOut()

        coEvery { session.currentUserId() } returns "account-B"
        coEvery { session.accessToken() } returns "access-B"
        coEvery { session.hasSession() } returns true
        every { installations.id } returns Tasks.forResult("fid-2")
        val activation = async(start = CoroutineStart.UNDISPATCHED) { registration.activate() }
        assertFalse(activation.isCompleted)
        verify(exactly = 1) { messaging.register() }

        pending.setResult(null)
        activation.await()
        advanceUntilIdle()
        coVerify(exactly = 1) { api.removePushInstallation(any(), PushRegistrationRequest("fid-1"), "Bearer access-A") }
        coVerify(exactly = 1) { api.registerPushInstallation(any(), PushRegistrationRequest("fid-2"), "Bearer access-B") }
        verify(exactly = 1) { messaging.unregister() }
    }

    @Test fun `uncertain upload is still removed on logout`() = runTest {
        coEvery { api.registerPushInstallation(any(), any(), any()) } throws IOException("response lost")
        registration.activate()
        registration.stop()
        registration.unregister("access-A")
        coVerify(exactly = 1) { api.removePushInstallation(any(), PushRegistrationRequest("fid-1"), "Bearer access-A") }
    }

    @Test fun `failed Firebase cleanup is retried before the next registration`() = runTest {
        registration.activate()
        every { messaging.unregister() } returns Tasks.forException(IOException("offline"))
        registration.stop()
        registration.unregister("access-A")
        every { messaging.unregister() } returns Tasks.forResult(null)
        registration.activate()

        verify(exactly = 2) { messaging.unregister() }
        verify(exactly = 2) { messaging.register() }
    }

    @Test fun `registration callback during upload is coalesced`() = runTest {
        val upload = CompletableDeferred<Unit>()
        coEvery { api.registerPushInstallation(any(), any(), any()) } coAnswers { upload.await() }
        val activation = async(start = CoroutineStart.UNDISPATCHED) { registration.activate() }
        registration.synchronize("fid-1")
        upload.complete(Unit)
        activation.await()
        coVerify(exactly = 1) { api.registerPushInstallation(any(), any(), any()) }
    }

    @Test fun `permission prompt choice survives account changes`() = runTest {
        assertTrue(registration.shouldAskPermission())
        registration.markPermissionAsked()
        registration.stop()
        registration.unregister(null)
        assertFalse(registration.shouldAskPermission())
    }

    @Test fun `expired local session invalidates Firebase without using another account token`() = runTest {
        registration.activate()
        registration.stop()
        coEvery { session.currentUserId() } returns null
        registration.clearSignedOut()
        verify(exactly = 1) { messaging.unregister() }
        coVerify(exactly = 0) { api.removePushInstallation(any(), any(), any()) }
    }

    @Test fun `delayed expiry cleanup cannot invalidate a signed in account`() = runTest {
        registration.activate()
        coEvery { session.hasSession() } returns true
        registration.clearSignedOut()
        verify(exactly = 0) { messaging.unregister() }
    }
}
