package com.axoo.features.profile.data

import com.axoo.core.session.SupabaseSessionStore
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.profile.domain.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import java.io.IOException
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProfileRepositorySessionTest {
    private val api = mockk<ProfileApi>()
    private val session = mockk<SupabaseSessionStore>()
    private val posts = mockk<PostRepository>(relaxed = true)
    private var sessionGeneration = 0L
    private var accountId: String? = "account-a"
        set(value) { field = value; sessionGeneration++ }

    private fun repository(): ProfileRepository {
        coEvery { session.currentUserId() } answers { accountId }
        every { session.generation } answers { sessionGeneration }
        return ProfileRepository(mockk(), api, posts, session)
    }

    @Test
    fun `memory profile is not returned for another account`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } returns user("account-a")
        repo.getMe()

        accountId = "account-b"

        assertNull(repo.getCachedUser())
    }

    @Test
    fun `new repository starts without a saved profile`() = runTest {
        coEvery { api.getMe() } returns user("account-a")
        val repo = repository()
        repo.getMe()

        assertEquals(user("account-a"), repo.getCachedUser())
        assertNull(repository().getCachedUser())
    }

    @Test
    fun `old response cannot overwrite new account after logout and login`() = runTest {
        val repo = repository()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<User>()
        coEvery { api.getMe() } coAnswers {
            started.complete(Unit)
            response.await()
        }
        val oldRequest = async { runCatching { repo.getMe() } }
        started.await()
        repo.clearCache()
        accountId = "account-b"
        coEvery { api.getMe() } returns user("account-b")
        repo.getMe()

        response.complete(user("account-a"))

        assertTrue(oldRequest.await().exceptionOrNull() is CancellationException)
        assertEquals(user("account-b"), repo.getCachedUser())
    }

    @Test
    fun `old response cannot restore cache after logout even when logging into same account`() = runTest {
        val repo = repository()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<User>()
        coEvery { api.getMe() } coAnswers {
            started.complete(Unit)
            response.await()
        }
        val oldRequest = async { runCatching { repo.getMe() } }
        started.await()
        repo.clearCache()
        response.complete(user("account-a"))

        assertTrue(oldRequest.await().exceptionOrNull() is CancellationException)
        assertNull(repo.getCachedUser())
    }

    @Test
    fun `profile response with a different user id is rejected`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } returns user("account-b")

        assertTrue(runCatching { repo.getMe() }.exceptionOrNull() is CancellationException)
        assertNull(repo.getCachedUser())
    }

    @Test
    fun `university id uses the profile already in memory`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } returns user("account-a")
        repo.getMe()

        assertEquals("university", repo.getUniversityId())
        coVerify(exactly = 1) { api.getMe() }
    }

    @Test
    fun `university id loads and caches the missing profile`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } returns user("account-a")

        assertEquals("university", repo.getUniversityId())
        assertEquals("university", repo.getUniversityId())
        assertEquals(user("account-a"), repo.getCachedUser())
        coVerify(exactly = 1) { api.getMe() }
    }

    @Test
    fun `university id failure leaves the cache empty and can retry`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } throws IOException("offline")

        assertTrue(runCatching { repo.getUniversityId() }.exceptionOrNull() is IOException)
        assertNull(repo.getCachedUser())
        coEvery { api.getMe() } returns user("account-a")
        assertEquals("university", repo.getUniversityId())
        coVerify(exactly = 2) { api.getMe() }
    }

    private fun user(id: String) = User(id, "Nickname $id", "university", createdAt = "2026-09-17")

    @Test fun `saving bio updates the current account cache`() = runTest {
        val repo = repository()
        val updated = user("account-a").copy(bio = "Bio 🐱")
        coEvery { api.updateBio(com.axoo.features.profile.data.dto.UpdateBioRequest("Bio 🐱")) } returns updated
        assertEquals(updated, repo.updateBio("Bio 🐱"))
        assertEquals(updated, repo.getCachedUser())
    }

    @Test fun `late bio save from a previous account cannot repopulate the cache`() = runTest {
        val repo = repository()
        val response = CompletableDeferred<User>()
        val started = CompletableDeferred<Unit>()
        coEvery { api.updateBio(any()) } coAnswers { started.complete(Unit); response.await() }
        val saving = async { runCatching { repo.updateBio("bio") } }
        started.await()
        accountId = "account-b"
        response.complete(user("account-a").copy(bio = "bio"))
        assertTrue(saving.await().exceptionOrNull() is CancellationException)
        assertNull(repo.getCachedUser())
    }

    @Test fun `public profiles never replace the signed in user cache`() = runTest {
        val repo = repository()
        coEvery { api.getMe() } returns user("account-a")
        repo.getMe()
        val profile = com.axoo.features.profile.domain.PublicProfile("other", "student", universityId = "university")
        every { posts.blockedAuthorIds } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        coEvery { api.getPublicProfile("other") } returns profile
        assertEquals(profile, repo.getPublicProfile("other"))
        assertEquals(user("account-a"), repo.getCachedUser())
    }

    @Test fun `profile blocked while loading is discarded`() = runTest {
        val repo = repository()
        val blocked = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
        every { posts.blockedAuthorIds } returns blocked
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<com.axoo.features.profile.domain.PublicProfile>()
        coEvery { api.getPublicProfile("other") } coAnswers { started.complete(Unit); response.await() }
        val loading = async { runCatching { repo.getPublicProfile("other") } }
        started.await()
        blocked.value = setOf("other")
        response.complete(com.axoo.features.profile.domain.PublicProfile("other", "student", universityId = "university"))
        assertTrue(loading.await().exceptionOrNull() is IllegalStateException)
    }

    @Test fun `bio emoji offsets convert between api unicode and android utf16`() = runTest {
        val repo = repository()
        val apiEntity = com.axoo.engine.emoji.text.MessageEntity(offset = 2, length = 1, customEmojiId = 42)
        val uiEntity = apiEntity.copy(offset = 3, length = 2)
        val response = user("account-a").copy(bio = "😀 🐱", bioEntities = listOf(apiEntity))
        coEvery { api.getMe() } returns response
        assertEquals(listOf(uiEntity), repo.getMe().bioEntities)
        val request = com.axoo.features.profile.data.dto.UpdateBioRequest("😀 🐱", listOf(apiEntity))
        coEvery { api.updateBio(request) } returns response
        assertEquals(listOf(uiEntity), repo.updateBio("😀 🐱", listOf(uiEntity)).bioEntities)
        assertEquals(listOf(uiEntity), repo.getCachedUser()?.bioEntities)
        coVerify(exactly = 1) { api.updateBio(request) }
    }
}
