package com.axoo.features.notifications.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.application.navigation.components.NativeBottomNavigationOverlay
import com.axoo.application.navigation.route.BottomTab
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.notifications.data.AppNotification
import com.axoo.engine.emoji.list.EmojiInlineConsumer
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.engine.emoji.text.AnimatedEmojiTextView
import com.axoo.ui.components.NativeSkeletonCardView
import com.axoo.ui.components.SkeletonType
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.formatting.formatTimeAgo
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface
import com.axoo.ui.theme.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class NotificationsHost(
    context: Context,
    private val viewModel: NotificationsViewModel,
    private val owner: LifecycleOwner,
    onTab: (BottomTab) -> Unit,
    onOpen: (AppNotification) -> Unit,
) : LinearLayout(context), NativeScreen {
    override val route: Route = Route.Notifications
    private val adapter = NotificationsAdapter(onOpen)
    private val list = EmojiRecyclerView(context).apply {
        setEmojiPlaybackGate(EmojiPlaybackGate.Inactive)
        layoutManager = LinearLayoutManager(context)
        adapter = this@NotificationsHost.adapter
        clipToPadding = false
        setPadding(0, dp(6), 0, dp(12))
        visibility = GONE
    }
    private val content = FrameLayout(context).apply { setBackgroundColor(NativeColors.surface) }
    private val skeleton = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        isEnabled = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(NativeSkeletonCardView(context, rows = 5, type = SkeletonType.Notification))
    }
    private val moreSkeleton = NativeSkeletonCardView(context, rows = 1, type = SkeletonType.Notification).apply {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val status = TextView(context).apply {
        visibility = GONE
        gravity = Gravity.CENTER
        textSize = 16f
        typeface = context.baloo2Typeface(600)
        setTextColor(NativeColors.textSecondary)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        setOnClickListener { if (viewModel.state.value.error) viewModel.retry() else viewModel.loadMore() }
    }
    private val bottom = NativeBottomNavigationBar(context)
    private var bottomInset = 0
    private var observer: Job? = null
    private var visible = false

    init {
        orientation = VERTICAL
        setBackgroundColor(NativeColors.surface)
        val heading = TextView(context).apply {
            text = "Notifiche"
            textSize = 22f
            typeface = context.baloo2Typeface(800)
            setTextColor(NativeColors.textPrimary)
            setBackgroundColor(NativeColors.card)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        content.addView(skeleton, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(moreSkeleton, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        content.addView(status, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        status.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBottomPadding() }
        moreSkeleton.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBottomPadding() }
        addView(NativeBottomNavigationOverlay(context, content, bottom) { inset ->
            bottomInset = inset
            updateBottomPadding()
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        bottom.bind(BottomTab.NOTIFICATIONS)
        bottom.setOnTabSelected(onTab)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !viewModel.state.value.error && (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() >= adapter.itemCount - 5) viewModel.loadMore()
            }
        })
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            heading.layoutParams.height = dp(44) + top
            heading.setPadding(dp(16), top, dp(16), 0)
            bottom.setNavigationBottomInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
    }

    override fun onScreenFullyVisible() {
        visible = true
        list.setEmojiPlaybackGate(EmojiPlaybackGate.FullyVisible)
        ViewCompat.requestApplyInsets(this)
        viewModel.open()
        if (observer != null) return
        observer = owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.unread.collect(bottom::bindUnreadCount) }
                launch { viewModel.state.collect { state ->
                    adapter.submitList(state.items) { list.post { if (visible) viewModel.acknowledge() } }
                    val empty = state.items.isEmpty()
                    skeleton.visibility = if (empty && state.loading) VISIBLE else GONE
                    moreSkeleton.visibility = if (!empty && state.loadingMore) VISIBLE else GONE
                    list.visibility = if (empty) GONE else VISIBLE
                    (status.layoutParams as FrameLayout.LayoutParams).gravity = if (empty) Gravity.CENTER else Gravity.BOTTOM
                    status.text = when {
                        state.loading || state.loadingMore -> ""
                        state.error -> "Impossibile caricare le notifiche. Tocca per riprovare."
                        state.items.isEmpty() -> "Non hai ancora notifiche"
                        state.cursor != null -> "Carica altre notifiche"
                        else -> ""
                    }
                    status.visibility = if (status.text.isEmpty()) GONE else VISIBLE
                    updateBottomPadding()
                } }
            }
        }
    }
    private fun updateBottomPadding() {
        val footer = when {
            moreSkeleton.visibility == VISIBLE -> moreSkeleton.measuredHeight
            status.visibility == VISIBLE && adapter.itemCount > 0 -> status.measuredHeight
            else -> 0
        }
        list.setPadding(0, dp(6), 0, dp(12) + bottomInset + footer)
        skeleton.setPadding(0, 0, 0, bottomInset)
        for (view in listOf(status, moreSkeleton)) {
            val params = view.layoutParams as FrameLayout.LayoutParams
            if (params.bottomMargin != bottomInset) {
                params.bottomMargin = bottomInset
                view.layoutParams = params
            }
        }
    }
    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) { list.setEmojiPlaybackGate(EmojiPlaybackGate.ActiveFrozen) }
    override fun onScreenHidden() { visible = false; list.setEmojiPlaybackGate(EmojiPlaybackGate.Inactive); viewModel.close(); observer?.cancel(); observer = null }
    override fun onScreenDestroyed() { onScreenHidden(); list.adapter = null }
}

private class NotificationsAdapter(private val onOpen: (AppNotification) -> Unit) : ListAdapter<AppNotification, NotificationHolder>(object : DiffUtil.ItemCallback<AppNotification>() {
    override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = NotificationHolder(NotificationPill(parent.context))
    override fun onBindViewHolder(holder: NotificationHolder, position: Int) { val item = getItem(position); holder.pill.bind(item); holder.pill.setOnClickListener { onOpen(item) } }
}
private class NotificationHolder(val pill: NotificationPill) : RecyclerView.ViewHolder(pill)

@SuppressLint("ViewConstructor")
private class NotificationPill(context: Context) : FrameLayout(context), EmojiInlineConsumer {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        applyNativePillSurface(cornerRadiusDp = 20f)
    }
    private val icon = ImageView(context)
    private val message = TextView(context).apply {
        textSize = 15f; typeface = context.interTypeface(600); setTextColor(NativeColors.textPrimary)
    }
    private val preview = AnimatedEmojiTextView(context).apply {
        setPlaybackAllowed(false)
        textSize = 14f; typeface = context.interTypeface(400); setTextColor(NativeColors.textSecondary)
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val dot = android.view.View(context).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(NativeColors.accent)
        }
    }
    private val time = TextView(context).apply {
        textSize = 12f; typeface = context.interTypeface(400); setTextColor(NativeColors.textSecondary)
    }
    init {
        layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) })
        row.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(14) })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(message)
            addView(preview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(time, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { leftMargin = dp(12) })
        isFocusable = true
    }
    fun bind(item: AppNotification) {
        dot.visibility = if (item.seenAt == null) VISIBLE else GONE
        message.text = item.message
        preview.setEmojiText(item.preview.replace('\n', ' '), item.previewEntities)
        preview.visibility = if (item.preview.isBlank()) GONE else VISIBLE
        time.text = formatTimeAgo(item.createdAt)
        val iconName = when (item.kind) {
            "post_appreciation" -> "arrow-up-v2"
            "comment_appreciation" -> "up-square-comment"
            "comment_reply" -> "reply-bold"
            else -> "comment"
        }
        icon.load("file:///android_asset/icons/$iconName.svg")
        icon.setColorFilter(if (item.seenAt == null) NativeColors.accent else NativeColors.textSecondary)
        contentDescription = "${item.message}. ${item.preview}. ${time.text}"
    }
    override fun setEmojiAnimating(value: Boolean) { preview.setPlaybackAllowed(value) }
}
