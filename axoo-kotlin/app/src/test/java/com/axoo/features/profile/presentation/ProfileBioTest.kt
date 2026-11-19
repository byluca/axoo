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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBioTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = mockk<ProfileRepository>()
    private val user = User("me", "student", "campus", createdAt = "now", bio = "Ciao")

    private fun model(current: User = user): ProfileViewModel {
        every { repository.getCachedUser() } returns current
        coEvery { repository.getMe() } returns current
        return ProfileViewModel(repository)
    }

    @Test fun `bio is optional and counts unicode code points`() = runTest {
        val model = model()
        advanceUntilIdle()
        model.beginBioEditing()
        assertFalse(model.bio.value.canSave)
        model.changeBio("  Ciao  ")
        assertFalse(model.bio.value.canSave)
        model.changeBio("😀".repeat(200))
        assertEquals(200, model.bio.value.length)
        assertTrue(model.bio.value.canSave)
        model.changeBio("😀".repeat(201))
        model.saveBio()
        assertFalse(model.bio.value.canSave)
        coVerify(exactly = 0) { repository.updateBio(any()) }
        coEvery { repository.updateBio("") } returns user.copy(bio = "")
        model.changeBio("")
        model.saveBio()
        advanceUntilIdle()
        assertTrue(model.bio.value.saved)
        assertEquals("", model.me.value.getOrNull()?.bio)
    }

    @Test fun `save trims freezes the draft ignores duplicate taps and updates shared profile`() = runTest {
        val model = model()
        advanceUntilIdle()
        val response = CompletableDeferred<User>()
        coEvery { repository.updateBio("Nuova bio 🐱") } coAnswers { response.await() }
        model.beginBioEditing()
        model.changeBio(" Nuova bio 🐱 ")
        model.saveBio()
        model.saveBio()
        model.changeBio("unexpected")
        runCurrent()
        assertTrue(model.bio.value.saving)
        assertEquals(" Nuova bio 🐱 ", model.bio.value.value)
        coVerify(exactly = 1) { repository.updateBio("Nuova bio 🐱") }
        response.complete(user.copy(bio = "Nuova bio 🐱"))
        advanceUntilIdle()
        assertEquals("Nuova bio 🐱", model.me.value.getOrNull()?.bio)
        assertTrue(model.bio.value.saved)
    }

    @Test fun `failed save preserves the draft and can retry`() = runTest {
        val model = model()
        advanceUntilIdle()
        coEvery { repository.updateBio("Nuova bio") } throws IOException("offline")
        model.beginBioEditing()
        model.changeBio("Nuova bio")
        model.saveBio()
        advanceUntilIdle()
        assertEquals("Nuova bio", model.bio.value.value)
        assertNotNull(model.bio.value.error)
        assertTrue(model.bio.value.canSave)
        coEvery { repository.updateBio("Nuova bio") } returns user.copy(bio = "Nuova bio")
        model.saveBio()
        advanceUntilIdle()
        assertTrue(model.bio.value.saved)
    }

    @Test fun `late save cannot close a new editor or overwrite its draft`() = runTest {
        val model = model()
        advanceUntilIdle()
        val response = CompletableDeferred<User>()
        coEvery { repository.updateBio("Old draft") } coAnswers { withContext(NonCancellable) { response.await() } }
        model.beginBioEditing()
        model.changeBio("Old draft")
        model.saveBio()
        runCurrent()
        model.stopBioEditing()
        model.beginBioEditing()
        model.changeBio("New draft")
        response.complete(user.copy(bio = "Old draft"))
        advanceUntilIdle()
        assertFalse(model.bio.value.saved)
        assertEquals("New draft", model.bio.value.value)
        assertEquals("Ciao", model.me.value.getOrNull()?.bio)
    }

    @Test fun `changing just the custom emoji can save and trimming keeps its position`() = runTest {
        val old = com.axoo.engine.emoji.text.MessageEntity(offset = 0, length = 2, customEmojiId = 42)
        val replacement = old.copy(customEmojiId = 43)
        val current = user.copy(bio = "🐱", bioEntities = listOf(old))
        val model = model(current)
        advanceUntilIdle()
        model.beginBioEditing()
        assertEquals(listOf(old), model.bio.value.entities)
        assertFalse(model.bio.value.canSave)
        model.changeBio("  🐱  ", listOf(replacement.copy(offset = 2)))
        assertTrue(model.bio.value.canSave)
        coEvery { repository.updateBio("🐱", listOf(replacement)) } returns current.copy(bioEntities = listOf(replacement))
        model.saveBio()
        advanceUntilIdle()
        assertTrue(model.bio.value.saved)
        assertEquals(listOf(replacement), model.me.value.getOrNull()?.bioEntities)
        coVerify(exactly = 1) { repository.updateBio("🐱", listOf(replacement)) }
    }
}
