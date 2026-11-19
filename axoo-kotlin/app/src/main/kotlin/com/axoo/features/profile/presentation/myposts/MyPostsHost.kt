package com.axoo.features.profile.presentation.myposts

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.features.posts.presentation.components.PostFeedView
import com.axoo.features.posts.domain.Post
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class MyPostsHost(
    context: Context,
    private val viewModel: MyPostsViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    onAuthor: (String) -> Unit = {},
) : LinearLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.MyPosts
    private val feed = PostFeedView(context)
    private val body = FrameLayout(context)
    private val status = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        typeface = context.baloo2Typeface(600)
        setTextColor(NativeColors.textSecondary)
        setBackgroundColor(NativeColors.surface)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        setOnClickListener { if (viewModel.state.value.error) viewModel.retry() else viewModel.loadMore() }
    }
    private var observer: Job? = null
    private var bottomInset = 0
    var onMenu: (Post, Boolean) -> Unit = { _, _ -> }

    init {
        orientation = VERTICAL
        setBackgroundColor(NativeColors.surface)
        val bar = FrameLayout(context).apply {
            setBackgroundColor(NativeColors.card)
            addView(ArrowBackButton(context).apply { setOnClickListener { onBack() } },
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(TextView(context).apply {
                text = "I miei post"
                textSize = 22f
                typeface = context.baloo2Typeface(800)
                setTextColor(NativeColors.textPrimary)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { leftMargin = dp(54); rightMargin = dp(54) })
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(feed, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(status, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        viewModel.ensureLoaded()
        feed.setCallbacks(viewModel::loadMore, onOpen, { viewModel.vote(it, "upvote") },
            { viewModel.vote(it, "downvote") }, onOpen, { post, _ -> onMenu(post, true) }, onAuthor,
            onPollVote = { post, option -> viewModel.votePoll(post, option) {
                com.axoo.ui.sheets.OverlayCard.error(this, "Voto non inviato. Riprova.")
            } })
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bar.setPadding(dp(6), insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, dp(6), 0)
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            bottomInset = bottom
            feed.setBottomNavigationPadding(bottom + if (status.isVisible) dp(64) else 0)
            (status.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bottom
                status.layoutParams = this
            }
            insets
        }
    }

    override fun onScreenFullyVisible() {
        ViewCompat.requestApplyInsets(this)
        feed.setScreenPlaybackGate(EmojiPlaybackGate.FullyVisible)
        if (observer != null) return
        observer = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    feed.render(state.posts, state.nextCursor != null && !state.error, state.loading, state.loadingMore, 0)
                    status.visibility = if (!state.loading && (state.error || state.posts.isEmpty() || state.nextCursor != null && !state.loadingMore)) VISIBLE else GONE
                    val empty = state.posts.isEmpty()
                    status.layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        if (empty) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
                        if (empty) Gravity.CENTER else Gravity.BOTTOM).apply { bottomMargin = bottomInset }
                    feed.setBottomNavigationPadding(bottomInset + if (status.isVisible) dp(64) else 0)
                    status.text = when {
                        state.error -> "Impossibile caricare i post. Tocca per riprovare."
                        state.posts.isEmpty() -> "Non hai ancora pubblicato post"
                        else -> "Carica altri post"
                    }
                }
            }
        }
    }

    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        feed.setScreenPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
    }

    override fun onScreenHidden() {
        observer?.cancel()
        observer = null
        feed.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
    }

    override fun onScreenDestroyed() { onScreenHidden(); feed.release() }
    override fun onBackPressed(): Boolean { onBack(); return true }
}
