package com.axoo.features.moderation.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.axoo.features.moderation.data.dto.ModerationQueueItem
import com.axoo.features.moderation.domain.ModerationReason
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.state.UiState
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

@SuppressLint("ViewConstructor")
internal class NativeModerationView(context: Context) : FrameLayout(context) {
    private var statusTopInset = 0
    private var navigationBottomInset = 0
    private var onBack: () -> Unit = {}
    private var onTabSelected: (ModerationTab) -> Unit = {}
    private var onRemove: (ModerationQueueItem) -> Unit = {}
    private var onDismiss: (ModerationQueueItem) -> Unit = {}
    private var onRestore: (ModerationQueueItem) -> Unit = {}
    private var onSuspend: (ModerationQueueItem) -> Unit = {}
    private var onUnsuspend: (ModerationQueueItem) -> Unit = {}
    private var onRetry: () -> Unit = {}

    private val topBar = FrameLayout(context).apply { setBackgroundColor(NativeColors.card) }
    private val back = ArrowBackButton(context).apply { setOnClickListener { onBack() } }
    private val title = label(OverlayCopy.moderationTitle, 22f, 800).apply { gravity = Gravity.CENTER }
    private var selectedTab = ModerationTab.Open
    private val scope = label("Cronologia", 17f, 400, NativeColors.accent).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        setOnClickListener {
            onTabSelected(if (selectedTab == ModerationTab.Open) ModerationTab.History else ModerationTab.Open)
        }
    }
    private val adapter = ModerationAdapter(
        onRemove = { onRemove(it) },
        onDismiss = { onDismiss(it) },
        onRestore = { onRestore(it) },
        onSuspend = { onSuspend(it) },
        onUnsuspend = { onUnsuspend(it) },
    )
    private val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = this@NativeModerationView.adapter
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_ALWAYS
    }
    private val status = label("", 17f, 400, NativeColors.textSecondary).apply {
        gravity = Gravity.CENTER
        setPadding(dp(30), dp(30), dp(30), dp(30))
    }

    init {
        setBackgroundColor(NativeColors.surface)
        topBar.addView(back, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = dp(6)
        })
        topBar.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.BOTTOM).apply {
            leftMargin = dp(104)
            rightMargin = dp(104)
        })
        topBar.addView(scope, LayoutParams(dp(96), dp(44), Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(8)
        })
        addView(topBar)
        addView(list)
        addView(status)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navigationBottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            list.setPadding(dp(16), dp(20), dp(16), navigationBottomInset + dp(32))
            requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setCallbacks(
        onBack: () -> Unit,
        onTabSelected: (ModerationTab) -> Unit,
        onRemove: (ModerationQueueItem) -> Unit,
        onDismiss: (ModerationQueueItem) -> Unit,
        onRestore: (ModerationQueueItem) -> Unit,
        onSuspend: (ModerationQueueItem) -> Unit,
        onUnsuspend: (ModerationQueueItem) -> Unit,
        onRetry: () -> Unit,
    ) {
        this.onBack = onBack
        this.onTabSelected = onTabSelected
        this.onRemove = onRemove
        this.onDismiss = onDismiss
        this.onRestore = onRestore
        this.onSuspend = onSuspend
        this.onUnsuspend = onUnsuspend
        this.onRetry = onRetry
    }

    fun render(state: ModerationUiState) {
        bindTabs(state.selectedTab)
        when (val items = state.items) {
            UiState.Loading -> {
                adapter.submit(emptyList(), state.selectedTab, state.busyTargetId)
                list.visibility = GONE
                status.text = "Carico le segnalazioni…"
                status.isClickable = false
                status.visibility = VISIBLE
            }
            is UiState.Error -> {
                adapter.submit(emptyList(), state.selectedTab, state.busyTargetId)
                list.visibility = GONE
                status.text = "Non riesco a caricare. Tocca per riprovare."
                status.isClickable = true
                status.setOnClickListener { onRetry() }
                status.visibility = VISIBLE
            }
            is UiState.Success -> {
                adapter.submit(items.data, state.selectedTab, state.busyTargetId)
                if (items.data.isEmpty()) {
                    list.visibility = GONE
                    status.text = if (state.selectedTab == ModerationTab.Open) {
                        "Nessuna segnalazione da controllare."
                    } else {
                        "Nessuna attività completata."
                    }
                    status.isClickable = false
                    status.setOnClickListener(null)
                    status.visibility = VISIBLE
                } else {
                    status.visibility = GONE
                    list.visibility = VISIBLE
                }
            }
        }
    }

    private fun bindTabs(selected: ModerationTab) {
        selectedTab = selected
        scope.text = if (selected == ModerationTab.Open) "Cronologia" else "Aperte"
    }

    private fun label(text: String, size: Float, weight: Int, color: Int = NativeColors.textPrimary) =
        TextView(context).apply {
            this.text = text
            textSize = size
            includeFontPadding = false
            setTextColor(color)
            typeface = context.baloo2Typeface(weight)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val topHeight = statusTopInset + dp(44)
        topBar.measure(exact(width), exact(topHeight))
        val contentTop = topHeight
        val contentHeight = (height - contentTop).coerceAtLeast(0)
        list.measure(exact(width), exact(contentHeight))
        status.measure(exact(width), exact(contentHeight))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        topBar.layout(0, 0, width, topBar.measuredHeight)
        val contentTop = topBar.measuredHeight
        list.layout(0, contentTop, width, bottom - top)
        status.layout(0, contentTop, width, bottom - top)
    }

    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
}

private class ModerationAdapter(
    private val onRemove: (ModerationQueueItem) -> Unit,
    private val onDismiss: (ModerationQueueItem) -> Unit,
    private val onRestore: (ModerationQueueItem) -> Unit,
    private val onSuspend: (ModerationQueueItem) -> Unit,
    private val onUnsuspend: (ModerationQueueItem) -> Unit,
) : RecyclerView.Adapter<ModerationAdapter.Holder>() {
    private var items = emptyList<ModerationQueueItem>()
    private var tab = ModerationTab.Open
    private var busyTargetId: String? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<ModerationQueueItem>, tab: ModerationTab, busyTargetId: String?) {
        this.items = items
        this.tab = tab
        this.busyTargetId = busyTargetId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ModerationCard(parent.context),
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.card.bind(
            item = item,
            tab = tab,
            busy = busyTargetId != null,
            onRemove = { onRemove(item) },
            onDismiss = { onDismiss(item) },
            onRestore = { onRestore(item) },
            onSuspend = { onSuspend(item) },
            onUnsuspend = { onUnsuspend(item) },
        )
    }

    class Holder(val card: ModerationCard) : RecyclerView.ViewHolder(card)
}

private class ModerationCard(context: Context) : LinearLayout(context) {
    private val contextLine = text(17f)
    private val body = text(12f).apply { setLineSpacing(dp(2).toFloat(), 1f) }
    private val detail = text(12f, NativeColors.textSecondary)
    private val menu = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        rotation = 90f
        setPadding(dp(10), dp(10), dp(10), dp(10))
        imageTintList = ColorStateList.valueOf(NativeColors.textSecondary)
        load("file:///android_asset/icons/dots-three.svg")
        isFocusable = true
    }

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(8), dp(8), dp(12))
        applyNativePillSurface(cornerRadiusDp = 20f)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(contextLine, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(menu, LayoutParams(dp(44), dp(44)))
        })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8)
        })
        addView(detail, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
            rightMargin = dp(8)
        })
        layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
    }

    fun bind(
        item: ModerationQueueItem,
        tab: ModerationTab,
        busy: Boolean,
        onRemove: () -> Unit,
        onDismiss: () -> Unit,
        onRestore: () -> Unit,
        onSuspend: () -> Unit,
        onUnsuspend: () -> Unit,
    ) {
        val contentType = when (item.targetType) {
            "post" -> "Post"
            "comment" -> "Commento"
            else -> "Account"
        }
        contextLine.text = "${item.authorNickname} · $contentType"
        body.text = item.body.ifBlank { "Contenuto non disponibile." }
        val reportLabel = if (item.reportCount == 1) "1 segnalazione" else "${item.reportCount} segnalazioni"
        val reason = if (tab == ModerationTab.History && item.decisionReason.isNotBlank()) {
            ModerationReason.label(item.decisionReason)
        } else {
            item.reasons.joinToString(" · ") { ModerationReason.label(it) }
        }
        val outcome = when {
            tab == ModerationTab.Open -> reportLabel
            item.action == "hide" || item.isHidden -> "Nascosto"
            item.action == "keep" -> "Lasciato online"
            item.action == "restore" -> "Ripristinato"
            item.action == "suspend" -> "Autore sospeso"
            item.action == "ban" -> "Autore bloccato"
            item.action == "unsuspend" -> "Autore riattivato"
            else -> "Gestito"
        }
        detail.text = listOf(outcome, reason).filter { it.isNotBlank() }.joinToString(" · ")
        val actions = mutableListOf<OptionsSheetAction>()
        if (tab == ModerationTab.Open) {
            actions.add(OptionsSheetAction("Lascia online", "checkmark.svg", onClick = onDismiss))
            if (item.targetType != "user") {
                actions.add(OptionsSheetAction(OverlayCopy.hide, "delete.svg", destructive = true, onClick = onRemove))
            }
            actions.add(OptionsSheetAction("Gestisci autore", "shield.svg", onClick = onSuspend))
        } else if (item.isHidden) {
            actions.add(OptionsSheetAction(OverlayCopy.restore, "checkmark.svg", onClick = onRestore))
        } else if ((item.action == "suspend" || item.action == "ban") && item.authorRestricted) {
            actions.add(OptionsSheetAction(OverlayCopy.unsuspend, "shield.svg", onClick = onUnsuspend))
        }
        menu.visibility = if (actions.isEmpty()) GONE else VISIBLE
        menu.isEnabled = !busy
        menu.setOnClickListener {
            OptionsSheet.present(anchor = this, title = OverlayCopy.moderationTitle, actions = actions)
        }
        alpha = if (busy) 0.6f else 1f
    }

    private fun text(size: Float, color: Int = NativeColors.textPrimary) = TextView(context).apply {
        textSize = size
        includeFontPadding = false
        setTextColor(color)
        typeface = if (size == 17f) context.baloo2Typeface(400) else context.interTypeface(400)
    }
}
