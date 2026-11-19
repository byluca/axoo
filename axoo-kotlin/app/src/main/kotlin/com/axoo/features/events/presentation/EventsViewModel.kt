package com.axoo.features.events.presentation

import android.net.Uri
import com.axoo.features.events.domain.CityEvent
import com.axoo.features.events.data.toCityEvent
import com.axoo.features.posts.data.PostPhotoRepository
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.presentation.posteditor.SelectedPostMedia
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.events.data.CampusEvent
import com.axoo.features.events.data.EventInput
import com.axoo.features.events.data.EventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class EventsState(
    val detail: CityEvent? = null,
    val detailError: Boolean = false,
    val items: List<CampusEvent> = emptyList(),
    val cursor: String? = null,
    val canManage: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(private val repository: EventsRepository, private val photos: PostPhotoRepository) : ViewModel() {
    private val mutable = MutableStateFlow(EventsState())
    val state = mutable.asStateFlow()
    private var editorUploadUri: Uri? = null
    private var editorUpload: ImageMeta? = null
    private var detailTask: Job? = null
    private var loadTask: Job? = null
    fun refresh() = load(null)
    fun loadMore() { if (!state.value.loading) state.value.cursor?.let(::load) }
    private fun load(cursor: String?) {
        loadTask?.cancel()
        mutable.update { it.copy(loading = true, error = false, saveError = null) }
        loadTask = viewModelScope.launch {
            try {
                val page = repository.page(cursor)
                ensureActive()
                mutable.update { it.copy(items = (if (cursor == null) page.events else it.items + page.events).distinctBy(CampusEvent::id),
                    cursor = page.nextCursor, canManage = page.canManage, loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = true) } }
        }
    }
    fun open(id: String) {
        detailTask?.cancel()
        mutable.update { it.copy(detail = it.items.firstOrNull { e -> e.id == id }?.toCityEvent(it.canManage), detailError = false) }
        detailTask = viewModelScope.launch {
            try {
                val event = repository.event(id)
                ensureActive()
                mutable.update { it.copy(detail = event.toCityEvent(), detailError = false, canManage = event.canManage) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(detail = null, detailError = true) } }
        }
    }
    fun save(id: String?, input: com.axoo.features.events.domain.EventInput, photo: Uri?, complete: (Boolean) -> Unit) {
        if (state.value.saving || !state.value.canManage) { complete(false); return }
        mutate(onResult = complete) {
            var prepared: SelectedPostMedia? = null
            try {
                val existing = id?.let { repository.event(it) }
                val poster = if (photo != null) {
                    if (editorUploadUri != photo || editorUpload == null) {
                        prepared = photos.prepare(photo)
                        editorUpload = photos.upload(requireNotNull(prepared)); editorUploadUri = photo
                    }
                    requireNotNull(editorUpload)
                } else existing?.poster
                val payload = EventInput(input.title, input.venue, input.description, input.startsAt, poster, input.endsAt, input.address, input.entryInfo)
                if (id == null) repository.create(payload) else repository.update(id, payload)
                editorUpload = null; editorUploadUri = null
                refresh()
            } finally { photos.discard(prepared) }
        }
    }
    fun delete(id: String) {
        if (!state.value.canManage || state.value.saving) return
        mutate { repository.delete(id); refresh() }
    }
    private fun mutate(onResult: (Boolean) -> Unit = {}, action: suspend () -> Unit) {
        mutable.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try { action(); mutable.update { it.copy(saving = false) }; onResult(true) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val forbidden = (error as? HttpException)?.code() == 403
                if ((error as? HttpException)?.code() == 400) { editorUpload = null; editorUploadUri = null }
                mutable.update { it.copy(saving = false, canManage = it.canManage && !forbidden,
                    saveError = if (forbidden) "Solo gli admin possono gestire gli eventi." else "Operazione non riuscita. Riprova.") }
                onResult(false)
            }
        }
    }
}
