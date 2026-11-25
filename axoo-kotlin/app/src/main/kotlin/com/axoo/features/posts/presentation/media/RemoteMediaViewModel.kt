package com.axoo.features.posts.presentation.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.posts.data.KlipyRepository
import com.axoo.features.posts.data.PersonalStickerRepository
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RemoteMediaError {
    LoadingFailed,
}

data class RemoteMediaState(
    val type: RemoteMediaType = RemoteMediaType.Gif,
    val query: String = "",
    val items: List<RemoteMedia> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextOffset: Int? = null,
    val error: RemoteMediaError? = null,
    val personalStickers: List<RemoteMedia> = emptyList(),
)

@HiltViewModel
class RemoteMediaViewModel @Inject constructor(
    private val klipyRepository: KlipyRepository,
    private val personalStickers: PersonalStickerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteMediaState())
    val state: StateFlow<RemoteMediaState> = _state.asStateFlow()
    private var requestJob: Job? = null
    private var stickerJob: Job? = null

    fun open(type: RemoteMediaType) {
        stickerJob?.cancel()
        _state.value = _state.value.copy(personalStickers = emptyList())
        if (type == RemoteMediaType.Sticker) {
            stickerJob = viewModelScope.launch {
                try { _state.value = _state.value.copy(personalStickers = personalStickers.list()) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* AUP and KLIPY remain available when the personal library is offline. */ }
            }
        }
        val current = _state.value
        if (current.type == type && current.query.isBlank() && (current.items.isNotEmpty() || current.isLoading)) return
        requestJob?.cancel()
        _state.value = RemoteMediaState(type = type, personalStickers = current.personalStickers)
        loadFirstPage(0)
    }

    fun search(query: String) {
        val value = query.take(100)
        if (value == _state.value.query) return
        _state.value = _state.value.copy(query = value)
        loadFirstPage(if (value.isBlank()) 0 else 350)
    }

    fun retry() = loadFirstPage(0)
    fun rememberSelection(media: RemoteMedia) = klipyRepository.rememberSelection(media)

    private fun loadFirstPage(delayMs: Long) {
        requestJob?.cancel()
        val current = _state.value.copy(items = emptyList(), isLoading = true, isLoadingMore = false, nextOffset = null, error = null)
        _state.value = current
        requestJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            try {
                val page = klipyRepository.load(current.type, current.query, 0)
                _state.value = _state.value.copy(items = page.items, isLoading = false, nextOffset = page.nextOffset)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.value = _state.value.copy(isLoading = false, error = RemoteMediaError.LoadingFailed) }
        }
    }

    fun loadMore() {
        val current = _state.value
        val next = current.nextOffset ?: return
        if (current.isLoading || current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true, error = null)
        requestJob = viewModelScope.launch {
            try {
                val page = klipyRepository.load(current.type, current.query, next)
                _state.value = _state.value.copy(items = current.items + page.items, isLoadingMore = false, nextOffset = page.nextOffset)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.value = _state.value.copy(isLoadingMore = false, error = RemoteMediaError.LoadingFailed) }
        }
    }
}
