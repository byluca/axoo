package com.axoo.features.profile.presentation

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.avatar.AvatarEditorSheet
import com.axoo.features.profile.presentation.components.NativeProfileRootView
import com.axoo.ui.sheets.OverlayCopy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class ProfileHost(
    context: Context,
    private val viewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val notifications: com.axoo.features.notifications.presentation.NotificationsViewModel? = null,
) : FrameLayout(context), NativeScreen {
    override val route: Route = Route.Profile
    private val rootView = NativeProfileRootView(context)

    private var onTab: (Tab) -> Unit = {}
    private var onMenuTapped: () -> Unit = {}
    private var onNicknameTapped: () -> Unit = {}
    private var onMyPostsTapped: () -> Unit = {}
    private var onBioTapped: () -> Unit = {}

    private var observerJob: Job? = null
    private var observerStarted = false
    private var avatarEditor: AvatarEditorSheet.Handle? = null

    init {
        addView(rootView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderState(viewModel.me.value)
        rootView.setEventSink { event ->
            if (avatarEditor != null) return@setEventSink
            when (event) {
                ProfileEvent.MenuTapped -> onMenuTapped()
                ProfileEvent.AvatarTapped -> showAvatarEditor()
                ProfileEvent.NicknameTapped -> onNicknameTapped()
                ProfileEvent.BioTapped -> onBioTapped()
                ProfileEvent.MyPostsTapped -> onMyPostsTapped()
                is ProfileEvent.BottomTabSelected -> onTab(event.tab)
            }
        }
    }

    fun setCallbacks(
        onTab: (Tab) -> Unit,
        onMenuTapped: () -> Unit,
        onNicknameTapped: () -> Unit,
        onMyPostsTapped: () -> Unit = {},
        onBioTapped: () -> Unit = {},
    ) {
        this.onTab = onTab
        this.onMenuTapped = onMenuTapped
        this.onNicknameTapped = onNicknameTapped
        this.onMyPostsTapped = onMyPostsTapped
        this.onBioTapped = onBioTapped
    }

    private fun startObserver() {
        if (observerStarted) return
        observerStarted = true

        observerJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notifications?.let { vm -> launch { vm.unread.collect(rootView::bindNotificationsCount) } }
                viewModel.me.onEach { renderState(it) }.launchIn(this)
            }
        }
    }

    private fun stopObserver() {
        observerJob?.cancel()
        observerJob = null
        observerStarted = false
    }

    override fun onScreenFullyVisible() {
        rootView.setBioActive(true)
        if (viewModel.me.value.getOrNull() == null) viewModel.refresh()
        startObserver()
        notifications?.refreshUnread()
    }

    override fun onScreenHidden() {
        rootView.setBioActive(false)
        dismissEditors()
        stopObserver()
    }

    override fun onScreenDestroyed() {
        rootView.setBioActive(false)
        dismissEditors()
        stopObserver()
    }

    private fun renderState(
        me: com.axoo.ui.state.UiState<com.axoo.features.profile.domain.User>,
    ) {
        val uiState = ProfileUiState(
            user = me.getOrNull(),
            isLoading = me is com.axoo.ui.state.UiState.Loading,
            isError = me is com.axoo.ui.state.UiState.Error,
        )
        rootView.render(uiState)
    }

    private fun showAvatarEditor() {
        if (avatarEditor != null) return
        val user = viewModel.me.value.getOrNull() ?: return
        avatarEditor = AvatarEditorSheet.present(
            anchor = rootView,
            user = user,
            onSave = { config ->
                val editor = avatarEditor ?: return@present
                editor.setSaving(true)
                viewModel.updateAvatar(
                    config = config,
                    onSuccess = { editor.dismiss() },
                    onFailure = { _ ->
                        editor.showError(OverlayCopy.avatarSaveError)
                    },
                )
            },
            onDismissed = { avatarEditor = null },
        )
    }

    private fun dismissEditors() {
        avatarEditor?.dismissImmediately()
        avatarEditor = null
    }

}
