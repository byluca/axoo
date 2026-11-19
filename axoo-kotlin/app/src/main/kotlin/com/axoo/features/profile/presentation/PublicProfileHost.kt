package com.axoo.features.profile.presentation

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.posts.presentation.report.ReportSheet
import com.axoo.features.profile.presentation.myposts.MyPostsViewModel
import com.axoo.features.posts.presentation.components.PostFeedView
import com.axoo.features.posts.domain.Post
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.features.profile.domain.PublicProfile
import com.axoo.features.profile.presentation.components.ProfileHeaderView
import com.axoo.features.profile.presentation.components.ProfileBioView
import com.axoo.features.profile.presentation.components.ProfileSkeletonHeaderView
import com.axoo.ui.avatar.isVerifiedAvatar
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import retrofit2.HttpException

internal class PublicProfileHost(
    context: Context,
    private val userId: String,
    private val viewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
    private val onEditBio: () -> Unit,
    private val postsModel: MyPostsViewModel,
    onOpen: (Post) -> Unit,
    onAuthor: (String) -> Unit,
) : LinearLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.UserProfile(userId)
    private var observer: Job? = null
    var onPostMenu: (Post, Boolean) -> Unit = { _, _ -> }
    private var request: Job? = null
    private var action: Job? = null
    private var profile: PublicProfile? = null
    private var ownProfile = false
    private var canShowPosts = false
    private var options: OptionsSheet.Handle? = null
    private var confirmation: OverlayCard.Handle? = null
    private var report: ReportSheet.Handle? = null
    private var bioView: ProfileBioView? = null
    private val content = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
    }
    private val feed = PostFeedView(context, header = content)
    private val postsStatus = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        typeface = context.baloo2Typeface(600)
        setTextColor(NativeColors.textSecondary)
        setBackgroundColor(NativeColors.surface)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        setOnClickListener { if (postsModel.state.value.error) postsModel.retry() else postsModel.loadMore() }
    }
    private val menu = ImageView(context).apply {
        contentDescription = "Opzioni profilo"
        imageTintList = ColorStateList.valueOf(NativeColors.textPrimary)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        load("file:///android_asset/icons/dots-three.svg")
        visibility = GONE
        setOnClickListener { showMenu() }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(NativeColors.surface)
        val bar = FrameLayout(context).apply {
            setBackgroundColor(NativeColors.card)
            addView(ArrowBackButton(context).apply { setOnClickListener { onBack() } },
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(TextView(context).apply {
                text = "Profilo"
                textSize = 22f
                typeface = context.baloo2Typeface(800)
                setTextColor(NativeColors.textPrimary)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
                leftMargin = dp(48)
                rightMargin = dp(48)
            })
            addView(menu, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL))
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(FrameLayout(context).apply {
            addView(feed, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(postsStatus, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        feed.setCallbacks(postsModel::loadMore, onOpen, { postsModel.vote(it, "upvote") },
            { postsModel.vote(it, "downvote") }, onOpen, { post, _ -> onPostMenu(post, ownProfile) }, onAuthor,
            onPollVote = { post, option -> postsModel.votePoll(post, option) {
                OverlayCard.error(this, "Voto non inviato. Riprova.")
            } })
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bar.setPadding(dp(6), insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, dp(6), 0)
            setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        showLoading()
    }

    override fun onScreenFullyVisible() {
        ViewCompat.requestApplyInsets(this)
        bioView?.setActive(true)
        feed.setScreenPlaybackGate(EmojiPlaybackGate.FullyVisible)
        if (observer == null) observer = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postsModel.state.collect { renderPosts() }
            }
        }
        if (request == null) loadProfile()
    }

    private fun loadProfile() {
        request?.cancel()
        request = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                canShowPosts = false
                renderPosts()
                bioView?.setActive(false)
                menu.visibility = GONE
                if (profile == null) showLoading()
                try {
                    val user = viewModel.getPublicProfile(userId)
                    val isOwn = viewModel.isOwnProfile(user.id)
                    ownProfile = isOwn
                    canShowPosts = isOwn || user.isProfilePublic
                    profile = user
                    content.removeAllViews()
                    content.addView(ProfileHeaderView(context).apply {
                        bind(user.nickname, user.universityName, buildAvatarUrl(user.id, user.avatarConfig), isVerifiedAvatar(user.avatarConfig))
                    }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    bioView = ProfileBioView(context, onEditBio.takeIf { isOwn }).apply {
                        bind(user.bio, user.bioEntities)
                        setActive(true)
                    }
                    content.addView(bioView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(16)
                    })
                    if (canShowPosts) content.addView(TextView(context).apply {
                        text = "Post pubblicati"
                        textSize = 20f
                        typeface = context.baloo2Typeface(700)
                        setTextColor(NativeColors.textPrimary)
                        ViewCompat.setAccessibilityHeading(this, true)
                    }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(20)
                    })
                    menu.visibility = if (isOwn) GONE else VISIBLE
                    if (canShowPosts) postsModel.refresh()
                    renderPosts()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    profile = null
                    val unavailable = error is IllegalStateException || (error is HttpException && error.code() == 404)
                    showStatus(if (unavailable) "Profilo non disponibile."
                        else "Impossibile caricare il profilo. Tocca per riprovare.", retry = !unavailable)
                }
                awaitCancellation()
            }
        }
    }

    private fun showLoading() {
        renderPosts()
        content.removeAllViews()
        content.addView(ProfileSkeletonHeaderView(context),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun showStatus(message: String, retry: Boolean = false) {
        renderPosts()
        content.removeAllViews()
        content.addView(TextView(context).apply {
            text = message
            textSize = 16f
            typeface = context.baloo2Typeface(600)
            setTextColor(NativeColors.textSecondary)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(48), dp(16), dp(48))
            if (retry) setOnClickListener { loadProfile() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun renderPosts() {
        val state = postsModel.state.value
        val ready = profile != null && canShowPosts
        feed.render(if (ready) state.posts else emptyList(), ready && state.nextCursor != null && !state.error,
            false, ready && state.loadingMore, 0)
        postsStatus.visibility = if (ready && (state.loading || state.error || state.nextCursor != null && !state.loadingMore)) VISIBLE else GONE
        postsStatus.text = when {
            state.loading -> "Caricamento post…"
            state.error -> "Impossibile caricare i post. Tocca per riprovare."
            else -> "Carica altri post"
        }
        feed.setBottomNavigationPadding(if (postsStatus.visibility == VISIBLE) dp(64) else 0)
    }

    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        feed.setScreenPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
        bioView?.setActive(false)
    }

    private fun showMenu() {
        val user = profile ?: return
        if (action?.isActive == true) return
        options = OptionsSheet.present(this, title = user.nickname, actions = listOf(
            OptionsSheetAction("Segnala profilo", "flag.svg", onClick = ::showReport),
            OptionsSheetAction(OverlayCopy.blockUser, "close-square.svg", destructive = true) {
                confirmation = OverlayCard.present(this, title = OverlayCopy.blockTitle, message = OverlayCopy.blockMessage,
                    primaryAction = OverlayCardAction(OverlayCopy.block, destructive = true) {
                        action = lifecycleOwner.lifecycleScope.launch {
                            try {
                                viewModel.blockUser(userId)
                                onBack()
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                confirmation = OverlayCard.error(this@PublicProfileHost, OverlayCopy.operationError)
                            }
                        }
                    },
                    secondaryAction = OverlayCardAction(OverlayCopy.cancel) {},
                )
            },
        ))
    }

    private fun showReport() {
        report = ReportSheet.present(this, onSubmit = { reason ->
            action = lifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.reportProfile(userId, reason.apiValue)
                    report?.complete()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    report?.showError(OverlayCopy.reportError)
                }
            }
        }, onDismissed = { report = null })
    }

    override fun onBackPressed(): Boolean { onBack(); return true }
    override fun onScreenHidden() {
        observer?.cancel()
        observer = null
        feed.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
        bioView?.setActive(false)
        request?.cancel()
        request = null
        action?.cancel()
        options?.dismiss()
        options = null
        confirmation?.dismiss()
        confirmation = null
        report?.dismiss()
        report = null
    }
    override fun onScreenDestroyed() { onScreenHidden(); feed.release() }
}
