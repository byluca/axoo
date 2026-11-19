package com.axoo.core.session

import com.axoo.core.session.SupabaseAuthApi
import com.axoo.core.session.SupabaseSessionResponse
import com.axoo.testing.MemoryPreferencesStore
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SupabaseSessionStoreTest {
    @Test fun `temporary refresh failure preserves credentials`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        coEvery { api.refreshSession(any(), any(), any()) } throws IOException("offline")
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("access", "refresh", 0))
        assertTrue(store.hasSession())
        try { store.accessToken(); fail("expected IOException") } catch (_: IOException) { }
        assertTrue(store.hasSession())
    }

    @Test fun `invalid refresh credentials end session`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        coEvery { api.refreshSession(any(), any(), any()) } throws HttpException(Response.error<Any>(400, "{}".toResponseBody()))
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("access", "refresh", 0))
        assertNull(store.accessToken())
        assertFalse(store.hasSession())
    }

    @Test fun `parallel callers refresh once`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        coEvery { api.refreshSession(any(), any(), any()) } coAnswers {
            delay(50)
            SupabaseSessionResponse("new-access", "new-refresh", 3600)
        }
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("access", "refresh", 0))
        val tokens = List(5) { async { store.accessToken() } }.awaitAll()
        assertEquals(List(5) { "new-access" }, tokens)
        coVerify(exactly = 1) { api.refreshSession(any(), any(), any()) }
    }

    @Test fun `cancelling refresh does not clear credentials`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        coEvery { api.refreshSession(any(), any(), any()) } throws CancellationException()
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("access", "refresh", 0))
        try { store.accessToken(); fail("expected cancellation") } catch (_: CancellationException) { }
        assertTrue(store.hasSession())
    }

    @Test fun `failed disk cleanup still immediately blocks all credential reads`() = runTest {
        val preferences = MemoryPreferencesStore()
        val store = SupabaseSessionStore(preferences, mockk())
        // {"sub":"account-a"}
        store.save(SupabaseSessionResponse("header.eyJzdWIiOiJhY2NvdW50LWEifQ.signature", "refresh", 3600))
        assertEquals("account-a", store.currentUserId())
        preferences.failWrites = true
        assertTrue(runCatching { store.clear() }.exceptionOrNull() is IOException)

        assertFalse(store.hasSession())
        assertNull(store.accessToken())
        assertNull(store.currentUserId())
        preferences.failWrites = false
        store.clear()
        assertFalse(SupabaseSessionStore(preferences, mockk()).hasSession())
    }

    @Test fun `logout completes without waiting for refresh and late tokens are discarded`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        val response = CompletableDeferred<SupabaseSessionResponse>()
        coEvery { api.refreshSession(any(), any(), any()) } coAnswers { response.await() }
        val preferences = MemoryPreferencesStore()
        val store = SupabaseSessionStore(preferences, api)
        store.save(SupabaseSessionResponse("old-access", "old-refresh", 0))
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.accessToken() }

        assertEquals("old-access", withTimeout(100) { store.clear() })
        assertFalse(store.hasSession())
        response.complete(SupabaseSessionResponse("late-access", "late-refresh", 3600))
        assertNull(refresh.await())
        assertFalse(SupabaseSessionStore(preferences, api).hasSession())
    }

    @Test fun `late refresh failure for account A cannot clear account B`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        val response = CompletableDeferred<SupabaseSessionResponse>()
        coEvery { api.refreshSession(any(), any(), any()) } coAnswers { response.await() }
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("account-a", "refresh-a", 0))
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.accessToken() }
        store.clear()
        store.save(SupabaseSessionResponse("account-b", "refresh-b", 3600))
        response.completeExceptionally(HttpException(Response.error<Any>(401, "{}".toResponseBody())))

        assertNull(refresh.await())
        assertEquals("account-b", store.accessToken())
        assertTrue(store.hasSession())
    }

    @Test fun `login started before logout cannot save a late session`() = runTest {
        val store = SupabaseSessionStore(MemoryPreferencesStore(), mockk())
        val oldGeneration = store.generation
        store.clear()
        val result = runCatching { store.save(SupabaseSessionResponse("late", "late-refresh", 3600), oldGeneration) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(store.hasSession())
    }

    @Test fun `old requests waiting for refresh cannot pick up new account credentials`() = runTest {
        val api = mockk<SupabaseAuthApi>()
        val response = CompletableDeferred<SupabaseSessionResponse>()
        coEvery { api.refreshSession(any(), any(), any()) } coAnswers { response.await() }
        val store = SupabaseSessionStore(MemoryPreferencesStore(), api)
        store.save(SupabaseSessionResponse("a", "refresh-a", 0))
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.accessToken() }
        val waitingRequest = async(start = CoroutineStart.UNDISPATCHED) { store.accessToken() }
        store.clear()
        store.save(SupabaseSessionResponse("b", "refresh-b", 3600))
        response.complete(SupabaseSessionResponse("late-a", "refresh-a", 3600))
        assertNull(refresh.await())
        assertNull(waitingRequest.await())
        assertEquals("b", store.accessToken())
    }

    @Test fun `conditional clearing and repeated logout preserve session boundaries`() = runTest {
        val store = SupabaseSessionStore(MemoryPreferencesStore(), mockk())
        val oldGeneration = store.save(SupabaseSessionResponse("a", "refresh-a", 3600))
        store.clear()
        assertNull(store.clear())
        store.save(SupabaseSessionResponse("b", "refresh-b", 3600))
        assertFalse(store.clearIfGeneration(oldGeneration))
        assertEquals("b", store.accessToken())
    }
}
