package com.axoo.features.moderation.presentation

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.moderation.data.dto.ModerationQueueItem
import com.axoo.features.moderation.domain.ModerationReason
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class ModerationHost(
    context: Context,
    private val viewModel: ModerationViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.Moderation
    private val root = NativeModerationView(context)
    private var observerJob: Job? = null

    init {
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.setCallbacks(
            onBack = onBack,
            onTabSelected = viewModel::selectTab,
            onRemove = ::confirmHide,
            onDismiss = { item -> actOnContent("keep", item) },
            onRestore = ::confirmRestore,
            onSuspend = ::chooseSuspension,
            onUnsuspend = ::confirmUnsuspend,
            onRetry = viewModel::refresh,
        )
    }

    override fun onScreenFullyVisible() {
        viewModel.refresh()
        if (observerJob != null) return
        observerJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.onEach(root::render).launchIn(this)
            }
        }
    }

    override fun onScreenHidden() {
        observerJob?.cancel()
        observerJob = null
    }

    override fun onScreenDestroyed() = onScreenHidden()

    override fun onBackPressed(): Boolean {
        onBack()
        return true
    }

    private fun confirmHide(item: ModerationQueueItem) {
        OverlayCard.present(
            anchor = root,
            title = OverlayCopy.moderationConfirmTitle,
            message = OverlayCopy.moderationConfirmMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.confirm,
                destructive = true,
                onClick = {
                    chooseModerationReason(OverlayCopy.moderationConfirmTitle) { reason ->
                        actOnContent("hide", item, reason)
                    }
                },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun confirmRestore(item: ModerationQueueItem) {
        OverlayCard.present(
            anchor = root,
            title = OverlayCopy.moderationConfirmTitle,
            message = OverlayCopy.moderationConfirmMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.confirm,
                onClick = { actOnContent("restore", item) },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun chooseSuspension(item: ModerationQueueItem) {
        OptionsSheet.present(
            anchor = root,
            title = OverlayCopy.moderationTitle,
            footer = item.body,
            actions = listOf(
                OptionsSheetAction(OverlayCopy.suspendDay, "shield.svg") {
                    confirmAccountAction(item, "suspend", 24, OverlayCopy.moderationConfirmTitle)
                },
                OptionsSheetAction(OverlayCopy.suspendWeek, "shield.svg") {
                    confirmAccountAction(item, "suspend", 168, OverlayCopy.moderationConfirmTitle)
                },
                OptionsSheetAction(
                    text = "Definitivamente",
                    iconAsset = "delete.svg",
                    destructive = true,
                ) {
                    confirmAccountAction(item, "ban", 0, OverlayCopy.moderationConfirmTitle)
                },
            ),
        )
    }

    private fun confirmUnsuspend(item: ModerationQueueItem) {
        OverlayCard.present(
            anchor = root,
            title = OverlayCopy.moderationConfirmTitle,
            message = OverlayCopy.moderationConfirmMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.confirm,
                onClick = {
                    viewModel.act(
                        action = "unsuspend",
                        targetType = "user",
                        targetId = item.authorUserId,
                        onFailure = ::showError,
                    )
                },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun confirmAccountAction(
        item: ModerationQueueItem,
        action: String,
        durationHours: Int,
        title: String,
    ) {
        OverlayCard.present(
            anchor = root,
            title = title,
            message = OverlayCopy.moderationConfirmMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.confirm,
                destructive = true,
                onClick = {
                    chooseModerationReason(OverlayCopy.moderationConfirmTitle) { reason ->
                        viewModel.act(
                            action = action,
                            targetType = "user",
                            targetId = item.authorUserId,
                            reason = reason,
                            durationHours = durationHours,
                            sourceTargetType = item.targetType,
                            sourceTargetId = item.targetId,
                            onFailure = ::showError,
                        )
                    }
                },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun actOnContent(action: String, item: ModerationQueueItem, reason: String = "") {
        viewModel.act(
            action = action,
            targetType = item.targetType,
            targetId = item.targetId,
            reason = reason,
            onFailure = ::showError,
        )
    }

    private fun chooseModerationReason(title: String, onSelected: (String) -> Unit) {
        OptionsSheet.present(
            anchor = root,
            title = title,
            footer = OverlayCopy.moderationConfirmMessage,
            actions = ModerationReason.entries.map { reason ->
                val label = when (reason.apiValue) {
                    "harassment" -> "Molestie"
                    "hate" -> "Odio"
                    else -> reason.label
                }
                OptionsSheetAction(label, "flag.svg") { onSelected(reason.apiValue) }
            },
        )
    }

    private fun showError(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        OverlayCard.present(
            anchor = root,
            title = OverlayCopy.errorTitle,
            message = OverlayCopy.moderationError,
            primaryAction = OverlayCardAction(text = OverlayCopy.ok, onClick = {}),
        )
    }
}
