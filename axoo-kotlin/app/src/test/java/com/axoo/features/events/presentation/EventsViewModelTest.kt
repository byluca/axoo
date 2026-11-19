package com.axoo.features.events.presentation

import com.axoo.features.events.data.EventsApi
import android.net.Uri
import com.axoo.features.posts.data.PostPhotoRepository
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.presentation.posteditor.PostMediaKind
import com.axoo.features.posts.presentation.posteditor.SelectedPostMedia
import io.mockk.verify
import com.axoo.features.events.data.CampusEvent
import com.axoo.features.events.data.EventsPage
import com.axoo.features.events.data.toCityEvent
import com.axoo.features.events.data.EventsRepository
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val api = mockk<EventsApi>()
    private fun event(id: String) = CampusEvent(id, "Evento", "Campus", "", "2027-01-01T12:00:00Z")
    private fun model(admin: Boolean = false): EventsViewModel {
        coEvery { api.getEvents(null) } returns EventsPage(listOf(event("1")), "next", admin)
        return EventsViewModel(EventsRepository(api), mockk(relaxed = true)).also { it.refresh() }
    }

    private fun input() = com.axoo.features.events.domain.EventInput("Titolo", "Descrizione", "", "2027-01-01T12:00:00Z", "2027-01-01T15:00:00Z", "Locale", "Via Roma", "Gratuito")
    @Test fun `read only users cannot publish or delete`() = runTest {
        val model = model(); advanceUntilIdle()
        var saved: Boolean? = null
        model.save(null, input(), null) { saved = it }; model.delete("1"); advanceUntilIdle()
        coVerify(exactly = 0) { api.createEvent(any()) }
        coVerify(exactly = 0) { api.deleteEvent(any()) }
        assertEquals(false, saved)
    }
    @Test fun `failed pagination preserves items and can be retried`() = runTest {
        val model = model()
        advanceUntilIdle()
        coEvery { api.getEvents("next") } throws IOException("offline")
        model.loadMore(); advanceUntilIdle()
        assertTrue(model.state.value.error)
        assertEquals(listOf("1"), model.state.value.items.map { it.id })
        coEvery { api.getEvents("next") } returns EventsPage(listOf(event("1"),event("2")))
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("1","2"),model.state.value.items.map { it.id })
        assertNull(model.state.value.cursor)
    }
    @Test fun `publishing prevents repeated taps and completes callback`() = runTest {
        val model = model(admin = true); advanceUntilIdle()
        val response = CompletableDeferred<CampusEvent>()
        coEvery { api.createEvent(any()) } coAnswers { response.await() }
        var saved = false
        model.save(null, input(), null) { saved = it }
        model.save(null, input(), null) {}; runCurrent()
        assertTrue(model.state.value.saving)
        coVerify(exactly = 1) { api.createEvent(any()) }
        response.complete(event("new")); advanceUntilIdle()
        assertTrue(saved); assertFalse(model.state.value.saving)
    }
    @Test fun `server denial removes stale admin controls`() = runTest {
        val model = model(admin = true); advanceUntilIdle()
        coEvery { api.createEvent(any()) } throws HttpException(Response.error<Any>(403,"{}".toResponseBody()))
        var saved = true
        model.save(null, input(), null) { saved = it }; advanceUntilIdle()
        assertFalse(model.state.value.canManage); assertFalse(saved)
        assertNotNull(model.state.value.saveError)
    }
    @Test fun `poster retry reuses successful upload and discards prepared file`() = runTest {
        val photos = mockk<PostPhotoRepository>(relaxed = true)
        val uri = mockk<Uri>()
        val selected = SelectedPostMedia(PostMediaKind.Photo, "file:///poster.jpg", "file:///poster.jpg", width = 800, height = 1200)
        val uploaded = ImageMeta("https://example.test/poster.jpg", width = 800, height = 1200, type = "photo", mimeType = "image/jpeg")
        coEvery { api.getEvents(null) } returns EventsPage(emptyList(), canManage = true)
        coEvery { photos.prepare(uri) } returns selected
        coEvery { photos.upload(selected) } returns uploaded
        coEvery { api.createEvent(any()) } throws IOException("offline")
        val model = EventsViewModel(EventsRepository(api), photos)
        model.refresh(); advanceUntilIdle()
        var saved = true
        model.save(null, input(), uri) { saved = it }; advanceUntilIdle()
        assertFalse(saved)
        coEvery { api.createEvent(any()) } returns event("saved")
        model.save(null, input(), uri) { saved = it }; advanceUntilIdle()
        assertTrue(saved)
        coVerify(exactly = 1) { photos.upload(selected) }
        coVerify(exactly = 2) { api.createEvent(match { it.poster == uploaded }) }
        verify(exactly = 1) { photos.discard(selected) }
    }
    @Test fun `original editor updates all fields and keeps existing poster`() = runTest {
        val poster = ImageMeta("https://example.test/poster.jpg", width = 800, height = 1200, type = "photo", mimeType = "image/jpeg")
        val original = event("1").copy(poster = poster, organizerId = "author", organizerName = "Admin", canManage = true)
        coEvery { api.getEvent("1") } returns original
        coEvery { api.updateEvent("1", any()) } returns original
        val model = model(admin = true); advanceUntilIdle()
        val input = com.axoo.features.events.domain.EventInput("Titolo", "Descrizione", requireNotNull(poster.url),
            "2027-01-01T12:00:00Z", "2027-01-01T15:00:00Z", "Locale", "Via Roma 1", "Gratuito")
        var saved = false
        model.save("1", input, null) { saved = it }; advanceUntilIdle()
        assertTrue(saved)
        coVerify { api.updateEvent("1", match { it.poster == poster && it.location == "Locale" && it.address == "Via Roma 1" && it.entryInfo == "Gratuito" && it.endsAt == input.endsAt }) }
        coVerify(exactly = 0) { api.createEvent(any()) }
    }
    @Test fun `detail uses real publisher and legacy events keep neutral attribution`() = runTest {
        assertNull(event("legacy").toCityEvent().organizerId)
        assertEquals("Organizzatore", event("legacy").toCityEvent().organizerName)
        val actual = event("1").copy(organizerId = "author", organizerName = "Nome reale", canManage = true)
        coEvery { api.getEvent("1") } returns actual
        val model = model(); advanceUntilIdle()
        model.open("1"); advanceUntilIdle()
        assertEquals("author", model.state.value.detail?.organizerId)
        assertEquals("Nome reale", model.state.value.detail?.organizerName)
        assertTrue(model.state.value.canManage)
    }

}
