package com.axoo.features.posts.presentation.media

import com.axoo.features.posts.data.KlipyRepository
import com.axoo.features.posts.data.PersonalStickerRepository
import com.axoo.features.posts.data.RemoteMediaPage
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteMediaViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = mockk<KlipyRepository>()

    @Test fun `personal library survives remote responses and clears when opening again offline`() = runTest {
        val personal = mockk<PersonalStickerRepository>()
        val pending = CompletableDeferred<RemoteMediaPage>()
        coEvery { personal.list() } returns listOf(media("mine"))
        coEvery { repository.load(RemoteMediaType.Sticker, "", 0, 30) } coAnswers { pending.await() }
        val model = RemoteMediaViewModel(repository, personal)
        model.open(RemoteMediaType.Sticker); runCurrent()
        assertEquals("mine", model.state.value.personalStickers.single().id)
        pending.complete(RemoteMediaPage(listOf(media("remote")), null)); advanceUntilIdle()
        assertEquals("mine", model.state.value.personalStickers.single().id)
        assertEquals("remote", model.state.value.items.single().id)
        coEvery { personal.list() } throws java.io.IOException("offline")
        model.open(RemoteMediaType.Sticker); advanceUntilIdle()
        assertTrue(model.state.value.personalStickers.isEmpty())
        assertEquals("remote", model.state.value.items.single().id)
    }

    @Test fun `meme discovery search and pagination use KLIPY and preserve provider order`() = runTest {
        coEvery { repository.load(RemoteMediaType.Meme, "", 0, 30) } returns RemoteMediaPage(listOf(media("first")), 2)
        coEvery { repository.load(RemoteMediaType.Meme, "", 2, 30) } returns RemoteMediaPage(listOf(media("next")), null)
        coEvery { repository.load(RemoteMediaType.Meme, "gatto", 0, 30) } returns RemoteMediaPage(listOf(media("found")), null)
        val model = RemoteMediaViewModel(repository, mockk(relaxed = true))
        model.open(RemoteMediaType.Meme)
        advanceUntilIdle()
        assertEquals("first", model.state.value.items.single().id)
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("first", "next"), model.state.value.items.map { it.id })
        model.search("gatto"); advanceUntilIdle()
        assertEquals("found", model.state.value.items.single().id)
        coVerify(exactly = 1) { repository.load(RemoteMediaType.Meme, "gatto", 0, 30) }
    }

    @Test fun `new search cancels previous request and clears old results immediately`() = runTest {
        val pending = CompletableDeferred<RemoteMediaPage>()
        coEvery { repository.load(RemoteMediaType.Gif, "", 0, 30) } coAnswers { pending.await() }
        coEvery { repository.load(RemoteMediaType.Gif, "seconda", 0, 30) } returns RemoteMediaPage(listOf(media("seconda")), null)
        val model = RemoteMediaViewModel(repository, mockk(relaxed = true))
        model.open(RemoteMediaType.Gif); runCurrent()
        model.search("prima"); model.search("seconda")
        assertTrue(model.state.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals("seconda", model.state.value.items.single().id)
        coVerify(exactly = 0) { repository.load(RemoteMediaType.Gif, "prima", 0, 30) }
    }

    @Test fun `pagination failure preserves items and retries the same provider page`() = runTest {
        coEvery { repository.load(RemoteMediaType.Meme, "", 0, 30) } returns RemoteMediaPage(listOf(media("first")), 2)
        coEvery { repository.load(RemoteMediaType.Meme, "", 2, 30) } throws java.io.IOException("offline")
        val model = RemoteMediaViewModel(repository, mockk(relaxed = true))
        model.open(RemoteMediaType.Meme); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals("first", model.state.value.items.single().id)
        assertEquals(2, model.state.value.nextOffset)
        assertFalse(model.state.value.isLoadingMore)
        coEvery { repository.load(RemoteMediaType.Meme, "", 2, 30) } returns RemoteMediaPage(listOf(media("next")), null)
        model.loadMore(); advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)
    }

    @Test fun `reopening resets search`() = runTest {
        coEvery { repository.load(RemoteMediaType.Gif, "", 0, 30) } returns RemoteMediaPage(listOf(media("trending")), null)
        coEvery { repository.load(RemoteMediaType.Gif, "ciao", 0, 30) } returns RemoteMediaPage(listOf(media("found")), null)
        val model = RemoteMediaViewModel(repository, mockk(relaxed = true))
        model.open(RemoteMediaType.Gif); advanceUntilIdle()
        model.search("ciao"); advanceUntilIdle()
        model.open(RemoteMediaType.Gif); advanceUntilIdle()
        assertEquals("", model.state.value.query)
        assertEquals("trending", model.state.value.items.single().id)
    }

    private fun media(id: String) = RemoteMedia(id, "https://static.klipy.com/$id.png",
        "https://static.klipy.com/$id.png", 320, 240, id, RemoteMediaType.Meme, provider = "klipy")
}
