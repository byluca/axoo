package com.axoo.features.moderation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.moderation.data.ModerationRepository
import com.axoo.features.moderation.data.dto.ModerationQueueItem
import com.axoo.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModerationTab(val apiStatus: String) {
    Open("open"),
    History("resolved"),
}

data class ModerationUiState(
    val selectedTab: ModerationTab = ModerationTab.Open,
    val items: UiState<List<ModerationQueueItem>> = UiState.Loading,
    val busyTargetId: String? = null,
)

@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val repository: ModerationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state.asStateFlow()

    fun selectTab(tab: ModerationTab) {
        if (_state.value.selectedTab == tab) return
        _state.value = _state.value.copy(selectedTab = tab, items = UiState.Loading)
        refresh()
    }

    fun refresh() {
        val tab = _state.value.selectedTab
        viewModelScope.launch {
            if (_state.value.items !is UiState.Success) {
                _state.value = _state.value.copy(items = UiState.Loading)
            }
            val result = runCatching { repository.queue(tab.apiStatus) }
            if (_state.value.selectedTab != tab) return@launch
            _state.value = _state.value.copy(
                items = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                ),
            )
        }
    }

    fun act(
        action: String,
        targetType: String,
        targetId: String,
        reason: String = "",
        durationHours: Int = 0,
        sourceTargetType: String? = null,
        sourceTargetId: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (_state.value.busyTargetId != null) return
        _state.value = _state.value.copy(busyTargetId = targetId)
        viewModelScope.launch {
            runCatching {
                repository.act(
                    action,
                    targetType,
                    targetId,
                    reason,
                    durationHours,
                    sourceTargetType,
                    sourceTargetId,
                )
            }.fold(
                onSuccess = {
                    _state.value = _state.value.copy(busyTargetId = null)
                    onSuccess()
                    refresh()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(busyTargetId = null)
                    onFailure(error)
                },
            )
        }
    }
}
