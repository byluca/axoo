package com.axoo.features.profile.presentation.blocked

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Schermata dedicata: l'elenco bloccati non è un'azione effimera da bottom sheet. */
internal class BlockedUsersHost(
    context: Context,
    private val viewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.BlockedUsers
    private val root = NativeBlockedUsersView(context)
    private var observerJob: Job? = null

    init {
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.setCallbacks(
            onBack = onBack,
            onUserMenu = ::showUnblockConfirmation,
        )
    }

    override fun onScreenFullyVisible() {
        viewModel.loadBlockedUsers()
        if (observerJob == null) {
            observerJob = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.blockedUsers.collect { root.render(it) }
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        onBack()
        return true
    }

    override fun onScreenHidden() {
        observerJob?.cancel()
        observerJob = null
        viewModel.stopBlockedUsers()
    }

    override fun onScreenDestroyed() = onScreenHidden()

    private fun showUnblockConfirmation(user: User) {
        if (viewModel.blockedUsers.value.unblockingUserId != null) return
        OverlayCard.present(
            anchor = root,
            title = "Sbloccare ${user.nickname}?",
            message = OverlayCopy.unblockMessage,
            primaryAction = OverlayCardAction(OverlayCopy.unblock) {
                viewModel.unblockUser(user.id)
            },
            secondaryAction = OverlayCardAction(OverlayCopy.cancel) {},
        )
    }
}
