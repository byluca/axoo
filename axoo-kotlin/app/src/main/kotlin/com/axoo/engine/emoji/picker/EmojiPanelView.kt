package com.axoo.engine.emoji.picker

import android.content.Context
import android.graphics.Color
import com.axoo.ui.theme.NativeColors
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.axoo.engine.emoji.catalog.Emoji
import com.axoo.engine.emoji.catalog.EmojiCategorySection
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.engine.emoji.list.EmojiScrollState
import com.axoo.ui.components.BottomFadeView
import com.axoo.ui.components.PickerPanelSurface
import com.axoo.ui.components.setControlShadow
import com.axoo.ui.components.TextCategoryBarView
import com.axoo.ui.components.TextCategoryItem
import com.axoo.ui.components.applyNativePillSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface EmojiPickerRow {
    data class Header(val title: String) : EmojiPickerRow
    data class Item(val emoji: Emoji, val category: String = "") : EmojiPickerRow
}

interface EmojiPanelDelegate {
    fun onEmojiPicked(emoji: Emoji)
    fun onBackspace()
}

class EmojiPanelView(
    private val context: Context,
    sections: List<EmojiCategorySection> = emptyList(),
    hasBackspace: Boolean = false,
    bottomPaddingDp: Int = 0,
    backgroundColor: Int = NativeColors.pickerBackground,
    cornerRadiusDp: Int = PickerPanelSurface.CORNER_RADIUS_DP,
    private val onEmojiSelected: (String) -> Unit = {},
    private val onBackspace: () -> Unit = {},
    private val delegate: EmojiPanelDelegate? = null,
    private val onMediaRequested: ((sticker: Boolean) -> Unit)? = null,
) {
    private companion object {
        const val CATEGORY_TOP_OFFSET_PX = 0
        const val CATEGORY_BAR_HEIGHT_DP = 44
    }

    private val root: FrameLayout
    private val contentFrame: FrameLayout
    private val categoryBarView: EmojiCategoryBar
    private val recyclerView: EmojiRecyclerView
    private val backspaceButton: EmojiBackspaceButton
    private val emojiAdapter: EmojiAdapter
    private val navigationBarFadeView = BottomFadeView(context, backgroundColor)

    private val divider = View(context).apply { setBackgroundColor(NativeColors.divider) }
    private val mediaTabs = TextCategoryBarView(context, fitWidth = true).apply {
        applyNativePillSurface(18f)
        bind(listOf(TextCategoryItem("emoji", "Emoji"), TextCategoryItem("gif", "GIF"), TextCategoryItem("sticker", "Sticker")), "emoji") { id ->
            if (id != "emoji") onMediaRequested?.invoke(id == "sticker")
        }
    }
    private var controlsVisible = true
    private var scrollDistance = 0
    private val baseSections = sections.filter { it.items.isNotEmpty() }
    private var categorySections = listOf(EmojiCategorySection("recent", "Recenti", "🕘", emptyList(), Emoji(-1, "ui/recent", "🕘"))) + baseSections
    private val pickerRows = mutableListOf<EmojiPickerRow>()
    private val categoryStartIndices = mutableListOf<Int>()
    private var rowToCategoryIndex = intArrayOf()

    private fun refreshRecents() {
        categorySections = listOf(categorySections.first().copy(items = RecentEmoji.items(context))) + baseSections
        pickerRows.clear()
        categoryStartIndices.clear()
        val mapping = mutableListOf<Int>()
        categorySections.forEachIndexed { index, section ->
            categoryStartIndices += pickerRows.size
            pickerRows += EmojiPickerRow.Header(section.label)
            pickerRows += section.items.map { EmojiPickerRow.Item(it, section.id) }
            repeat(1 + section.items.size) { mapping += index }
        }
        rowToCategoryIndex = mapping.toIntArray()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeCategoryIndex = 0
    private var lastReportedCategory = -1
    private var backspaceTicks = 0
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            dispatchBackspace()
            backspaceTicks++
            val delayMs = if (backspaceTicks < 10) 300L - backspaceTicks * 25L else 50L
            handler.postDelayed(this, delayMs.coerceAtLeast(50L))
        }
    }

    private val density = context.resources.displayMetrics.density
    private val baseBottomPaddingPx = (bottomPaddingDp * density).toInt()
    private var navigationBottomInsetPx = 0

    private fun dispatchLegacyStringSelection(emoji: Emoji) {
        onEmojiSelected(emoji.fallback.ifBlank { emoji.name })
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            reportVisibleCategory()
            if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE ||
                this@EmojiPanelView.recyclerView.emojiScrollState == EmojiScrollState.PROGRAMMATIC_SCROLL) return
            if ((scrollDistance > 0) != (dy > 0)) scrollDistance = 0
            scrollDistance += dy
            if (!recyclerView.canScrollVertically(-1)) setControlsVisible(true)
            else if (kotlin.math.abs(scrollDistance) >= dp(8)) setControlsVisible(scrollDistance < 0)
        }
    }

    init {
        refreshRecents()
        val spanCount = 6
        val cellSizePx = context.resources.displayMetrics.widthPixels / spanCount

        emojiAdapter = EmojiAdapter(
            rows = pickerRows,
            cellSizePx = cellSizePx,
            appContext = context.applicationContext,
            onEmojiClick = pick@{ pick ->
                RecentEmoji.record(context, pick)
                val panelDelegate = delegate
                if (panelDelegate != null) {
                    panelDelegate.onEmojiPicked(pick)
                } else {
                    dispatchLegacyStringSelection(pick)
                }
            },
        )

        categoryBarView = EmojiCategoryBar(
            context = context,
            onCategoryClicked = { index ->
                showCategory(index)
            },
        )
        buildCategoryBar()
        categoryBarView.ensureVisible()
        recyclerView = EmojiRecyclerView(context).apply {
            enablePickerPlayback()
            setBackgroundColor(Color.TRANSPARENT)
            layoutManager = GridLayoutManager(context, spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = when (emojiAdapter.getItemViewType(position)) {
                        EmojiAdapter.TYPE_HEADER -> spanCount
                        else -> 1
                    }
                }
            }
            setHasFixedSize(true)
            setItemViewCacheSize(12)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, dp(CATEGORY_BAR_HEIGHT_DP), 0, baseBottomPaddingPx + dp(68))
            adapter = emojiAdapter
            setEmojiPlaybackController(emojiAdapter)
            setEmojiPlaybackGate(EmojiPlaybackGate.Inactive)
            addOnScrollListener(scrollListener)
        }
        backspaceButton = EmojiBackspaceButton(context).apply {
            visibility = if (hasBackspace) View.VISIBLE else View.GONE
            viewScope.launch {
                val bmp = CoilBitmapLoader.load(
                    context = context,
                    data = CoilBitmapLoader.assetData("icons/backspace.svg"),
                    sizePx = dp(30),
                )
                if (bmp != null) {
                    post { setIconBitmap(bmp) }
                }
            }
            setOnClickListener { dispatchBackspace() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        startBackspaceRepeat()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        stopBackspaceRepeat()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.x >= view.width || event.y < 0 || event.y >= view.height) {
                            view.isPressed = false
                            stopBackspaceRepeat()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        contentFrame = FrameLayout(context).apply {
            clipToOutline = true
            addView(
                recyclerView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            if (hasBackspace) {
                addView(
                    backspaceButton,
                    FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END).apply {
                        rightMargin = dp(14)
                        bottomMargin = baseBottomPaddingPx + dp(14)
                    },
                )
            }
        }

        root = PickerPanelSurface(
            context = context,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp,
        ).apply {
            setControlShadow(dp(cornerRadiusDp).toFloat())
            addView(
                contentFrame,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                categoryBarView.getView().apply {
                    setBackgroundColor(backgroundColor)
                    elevation = 0f
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(CATEGORY_BAR_HEIGHT_DP),
                    Gravity.TOP,
                ),
            )
            addView(
                navigationBarFadeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
            addView(divider,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    topMargin = dp(CATEGORY_BAR_HEIGHT_DP)
                    leftMargin = dp(8); rightMargin = dp(8)
                })
            categoryBarView.getView().bringToFront()
            if (onMediaRequested != null) addView(mediaTabs,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = baseBottomPaddingPx + dp(22)
                })
        }

        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            if (onMediaRequested != null) {
                val width = minOf(dp(180), (right - left - dp(156)).coerceAtLeast(dp(132)))
                if (mediaTabs.layoutParams.width != width) {
                    mediaTabs.layoutParams = mediaTabs.layoutParams.apply { this.width = width }
                }
            }
        }
        recyclerView.post { recyclerView.surfaceCoordinator.markDirty() }

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (isActive) {
                    recyclerView.surfaceCoordinator.onPickerOpen()
                } else {
                    recyclerView.surfaceCoordinator.onPickerClose()
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                stopBackspaceRepeat()
                backspaceButton.isPressed = false
                emojiAdapter.pauseAndReleaseAllVisible(recyclerView)
                recyclerView.surfaceCoordinator.onPickerClose()
            }
        })
    }

    private fun buildCategoryBar() {
        val items = mutableListOf<EmojiCategoryBar.CategoryItem>()
        for ((index, section) in categorySections.withIndex()) {
            items += EmojiCategoryBar.CategoryItem(
                index = index,
                id = section.id,
                label = section.label,
            )
        }
        categoryBarView.setCategories(items)
    }

    private fun showCategory(index: Int) {
        activeCategoryIndex = index
        updateCategorySelection()

        scrollToCategoryIndex(index)
    }

    private fun scrollToCategoryIndex(index: Int) {
        val flatIndex = categoryStartIndices.getOrNull(index) ?: return
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        recyclerView.beginProgrammaticScroll()
        lm.scrollToPositionWithOffset(flatIndex, CATEGORY_TOP_OFFSET_PX)
        recyclerView.post {
            recyclerView.finishProgrammaticScrollIfIdle()
            recyclerView.surfaceCoordinator.markDirty()
        }
    }

    private fun updateCategorySelection() {
        categoryBarView.setActiveCategoryIndex(activeCategoryIndex)
    }

    private fun reportVisibleCategory() {
        if (!isActive || categoryStartIndices.isEmpty()) return
        if (recyclerView.emojiScrollState == EmojiScrollState.PROGRAMMATIC_SCROLL) return
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        val rowIndex = if (!recyclerView.canScrollVertically(1)) {
            rowToCategoryIndex.lastIndex
        } else {
            lm.findFirstVisibleItemPosition()
        }
        if (rowIndex !in rowToCategoryIndex.indices) return
        val catIndex = rowToCategoryIndex[rowIndex]

        if (catIndex != lastReportedCategory) {
            lastReportedCategory = catIndex
            activeCategoryIndex = catIndex
            updateCategorySelection()
        }
    }

    fun getView(): View = root

    private var isActive: Boolean = false
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive

    /**
     * Da chiamare anche quando il picker viene nascosto senza detach, per rilasciare le lease.
     */
    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) {
            emojiAdapter.pauseAndReleaseAllVisible(recyclerView)
            refreshRecents()
            emojiAdapter.notifyDataSetChanged()
            scrollToCategoryIndex(0)
            categoryBarView.ensureVisible()
            setControlsVisible(true)
            scrollDistance = 0
        }
        applyRecyclerPlaybackGate()
        if (active) {
            recyclerView.surfaceCoordinator.onPickerOpen()
            recyclerView.post { recyclerView.surfaceCoordinator.markDirty() }
        } else {
            stopBackspaceRepeat()
            backspaceButton.isPressed = false
            emojiAdapter.pauseAndReleaseAllVisible(recyclerView)
            recyclerView.surfaceCoordinator.onPickerClose()
            // La prossima apertura parte sempre dalla prima categoria.
            recyclerView.stopScroll()
            activeCategoryIndex = 0
            lastReportedCategory = 0
            categoryBarView.setActiveCategoryIndex(0)
            scrollToCategoryIndex(0)
            categoryBarView.ensureVisible()
        }
    }

    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        if (playbackGate == gate) return
        playbackGate = gate
        applyRecyclerPlaybackGate()
    }

    fun setNavigationBottomInset(bottomInsetPx: Int) {
        val safeInset = bottomInsetPx.coerceAtLeast(0)
        if (navigationBottomInsetPx == safeInset) return
        navigationBottomInsetPx = safeInset
        recyclerView.setPadding(
            recyclerView.paddingLeft,
            recyclerView.paddingTop,
            recyclerView.paddingRight,
            baseBottomPaddingPx + safeInset + dp(68),
        )
        val contentParams = contentFrame.layoutParams as? FrameLayout.LayoutParams
        if (contentParams != null) {
            contentParams.bottomMargin = 0
            contentFrame.layoutParams = contentParams
        }
        (mediaTabs.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.bottomMargin = baseBottomPaddingPx + safeInset + dp(22)
            mediaTabs.layoutParams = it
        }
        val backspaceParams = backspaceButton.layoutParams as? FrameLayout.LayoutParams
        if (backspaceParams != null) {
            backspaceParams.bottomMargin = baseBottomPaddingPx + safeInset + dp(14)
            backspaceButton.layoutParams = backspaceParams
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        if (!visible) { stopBackspaceRepeat(); backspaceButton.isPressed = false }
        listOf(categoryBarView.getView(), divider, backspaceButton, mediaTabs).forEach { view ->
            view.isEnabled = visible
            if (visible) view.visibility = View.VISIBLE
            view.animate().alpha(if (visible) 1f else 0f).setDuration(160).withEndAction {
                if (!controlsVisible) view.visibility = View.INVISIBLE
            }.start()
        }
    }

    private fun applyRecyclerPlaybackGate() {
        recyclerView.setEmojiPlaybackGate(
            if (isActive) {
                playbackGate
            } else {
                EmojiPlaybackGate.Inactive
            },
        )
    }

    fun dispose() {
        stopBackspaceRepeat()
        handler.removeCallbacksAndMessages(null)
        categoryBarView.dispose()
        viewScope.cancel()
        recyclerView.setEmojiPlaybackController(null)
        recyclerView.adapter = null
        recyclerView.removeOnScrollListener(scrollListener)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun dispatchBackspace() {
        delegate?.onBackspace() ?: onBackspace()
    }

    private fun startBackspaceRepeat() {
        dispatchBackspace()
        backspaceTicks = 0
        handler.removeCallbacks(backspaceRepeatRunnable)
        handler.postDelayed(backspaceRepeatRunnable, 300L)
    }

    private fun stopBackspaceRepeat() {
        handler.removeCallbacks(backspaceRepeatRunnable)
        backspaceTicks = 0
    }
}
