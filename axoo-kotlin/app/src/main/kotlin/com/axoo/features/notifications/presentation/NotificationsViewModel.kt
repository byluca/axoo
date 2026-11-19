package com.axoo.features.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.notifications.data.AppNotification
import com.axoo.features.notifications.data.NotificationsRepository
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.data.PostChange
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsState(
    val items: List<AppNotification> = emptyList(),
    val cursor: String? = null,
    val seenThrough: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val failedLoadMore: Boolean = false,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    postRepository: PostRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state = _state.asStateFlow()
    val unread = repository.unread
    private var request: Job? = null
    private var countRequest: Job? = null
    private var seenRequest: Job? = null
    private var acknowledged: String? = null
    private var dirty = true
    private var visible = false

    init {
        repository.prepareSession()
        viewModelScope.launch { repository.changes.collect {
            dirty = true
            if (visible) refresh()
        } }
        viewModelScope.launch {
            combine(postRepository.blockedAuthorIds, postRepository.hiddenPostIds) { blocked, hidden -> blocked to hidden }
                .drop(1).collect { (blocked, hidden) ->
                    _state.update { state -> state.copy(items = state.items.filterNot { it.actorId in blocked || it.postId in hidden }) }
                    invalidate()
                }
        }
        viewModelScope.launch {
            postRepository.changes.collect { change ->
                if (change is PostChange.Deleted) {
                    _state.update { state -> state.copy(items = state.items.filterNot { it.postId == change.id }) }
                    invalidate()
                }
            }
        }
    }

    private fun invalidate() { dirty = true; if (visible) refresh() else refreshUnread() }

    fun open() { visible = true; if (dirty) refresh() else refreshUnread() }
    fun close() { visible = false }
    fun foreground() { dirty = true; if (visible) refresh() else refreshUnread() }

    fun refreshUnread() {
        countRequest?.cancel()
        countRequest = viewModelScope.launch {
            try { repository.refreshUnread() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Preserve the last confirmed count. */ }
        }
    }

    fun refresh() { request?.cancel(); load(null) }
    fun loadMore() { if (request?.isActive != true) state.value.cursor?.let(::load) }
    fun retry() { if (state.value.failedLoadMore) loadMore() else refresh() }

    private fun load(cursor: String?) {
        _state.update { it.copy(loading = cursor == null, loadingMore = cursor != null, error = false) }
        request = viewModelScope.launch {
            try {
                val page = repository.page(cursor)
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(
                    items = (if (cursor == null) page.notifications else it.items + page.notifications).distinctBy(AppNotification::id),
                    cursor = page.nextCursor?.takeIf(String::isNotBlank),
                    seenThrough = if (cursor == null) page.seenThrough?.takeIf(String::isNotBlank) else it.seenThrough,
                    loading = false, loadingMore = false, failedLoadMore = false,
                ) }
                if (cursor == null) acknowledged = null
                dirty = false
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = true, failedLoadMore = cursor != null) }
            }
        }
    }

    /** Called after the loaded list is displayed, never on an unsuccessful open. */
    fun acknowledge() {
        val through = state.value.seenThrough ?: return
        if (!visible || state.value.loading || state.value.error || acknowledged == through || seenRequest?.isActive == true) return
        seenRequest = viewModelScope.launch {
            var success = false
            try { repository.seen(through); acknowledged = through; success = true }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Keep the count; the next successful display retries. */ }
            finally {
                seenRequest = null
                if (success && state.value.seenThrough != through) acknowledge()
            }
        }
    }
}
