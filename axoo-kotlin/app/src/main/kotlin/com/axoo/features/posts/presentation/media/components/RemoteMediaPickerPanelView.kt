package com.axoo.features.posts.presentation.media.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import coil.load
import com.axoo.R
import com.axoo.engine.emoji.list.BaseRecyclerView
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.AupStickers
import com.axoo.features.posts.presentation.stickers.StickerCreatorActivity
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.media.RemoteMediaError
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.ui.sheets.BottomSheetTypography
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.sheets.applySheetTypography
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.baloo2Typeface

@SuppressLint("ViewConstructor")
internal class RemoteMediaPickerPanelView(
    context: Context,
    onRemoteMediaTap: (RemoteMedia) -> Unit,
    private val onSearchQueryChanged: (String) -> Unit,
    private val onLoadMore: () -> Unit,
    private val onRetry: () -> Unit,
) : FrameLayout(context) {
    private var suppressSearchCallback = false
    private val selectMedia = onRemoteMediaTap
    private var creator: androidx.activity.result.ActivityResultLauncher<Intent>? = null

    private fun createSticker() {
        clearSearchFocus()
        var owner = context
        while (owner is ContextWrapper && owner !is ComponentActivity) owner = owner.baseContext
        val activity = owner as? ComponentActivity ?: return
        if (creator == null) creator = activity.activityResultRegistry.register(
            "create-sticker-${java.util.UUID.randomUUID()}", ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val id = result.data?.getStringExtra("id")
            val url = result.data?.getStringExtra("url")
            if (result.resultCode == android.app.Activity.RESULT_OK && id != null && url != null) {
                selectMedia(RemoteMedia(id, url, url, 512, 512, "Sticker personale", RemoteMediaType.Sticker,
                    provider = "axoo", mimeType = "image/png"))
            }
        }
        creator?.launch(Intent(activity, StickerCreatorActivity::class.java))
    }

    private val remoteAdapter: RemoteMediaGridAdapter = RemoteMediaGridAdapter(
        context = context,
        onMediaTap = onRemoteMediaTap,
        spacingPx = context.dp(2),
        onPreviewVisibilityChanged = { aupAdapter.setPreviewVisible(it); personalAdapter.setPreviewVisible(it) },
    )
    private val aupAdapter: RemoteMediaGridAdapter = RemoteMediaGridAdapter(context, onRemoteMediaTap, context.dp(2),
        onPreviewVisibilityChanged = { remoteAdapter.setPreviewVisible(it); personalAdapter.setPreviewVisible(it) }).apply { submit(AupStickers.all) }
    private val personalAdapter = RemoteMediaGridAdapter(context, { media ->
        if (media == CreateStickerTile) createSticker() else onRemoteMediaTap(media)
    }, context.dp(2), onPreviewVisibilityChanged = { remoteAdapter.setPreviewVisible(it); aupAdapter.setPreviewVisible(it) })
    private val remoteStatus = RemoteMediaStatusAdapter(onRetry)
    private val stickerSections = ConcatAdapter(MediaSectionHeader("I tuoi sticker"), personalAdapter, MediaSectionHeader("AUP"), aupAdapter,
        MediaSectionHeader("KLIPY"), remoteAdapter, remoteStatus)
    private var showingAup = false

    private val recyclerView = BaseRecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = remoteAdapter
        itemAnimator = null
        clipToPadding = false
        setAxooPadding(bottom = context.dp(12))
        addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val width = (right - left - view.paddingLeft - view.paddingRight).coerceAtLeast(0)
            // Items may arrive before the first layout. Notify the adapter after
            // RecyclerView has finished that pass, so its newly built rows are laid out.
            view.post { remoteAdapter.setContentWidth(width); aupAdapter.setContentWidth(width); personalAdapter.setContentWidth(width) }
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleMediaItem = manager.findLastVisibleItemPosition()
                if (remoteAdapter.itemCount > 0 && lastVisibleMediaItem >=
                    (recyclerView.adapter?.itemCount ?: 0) - LOAD_MORE_THRESHOLD
                ) {
                    onLoadMore()
                }
            }
        })
    }

    private val searchField = EditText(context).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(NativeColors.textPrimary)
        setHintTextColor(NativeColors.textSecondary)
        applySheetTypography(BottomSheetTypography.search)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 0)
        background = null
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressSearchCallback) onSearchQueryChanged(text?.toString().orEmpty())
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
        setOnEditorActionListener { view, actionId, _ ->
            val shouldCloseKeyboard = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE
            if (shouldCloseKeyboard) {
                view.clearFocus()
                context.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(view.windowToken, 0)
            }
            shouldCloseKeyboard
        }
    }

    private val searchIcon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageTintList = ColorStateList.valueOf(NativeColors.textSecondary)
        load("file:///android_asset/icons/search.svg")
    }

    private val searchBox = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(12), 0, context.dp(12), 0)
        background = GradientDrawable().apply {
            setColor(NativeColors.surface)
            cornerRadius = context.dp(10).toFloat()
        }
        addView(searchIcon, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
            marginEnd = context.dp(8)
        })
        addView(searchField, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
    }

    private val controlsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = null
        clipToOutline = true
        addView(
            searchBox,
            LinearLayout.LayoutParams(0, context.dp(36), 0.9f).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

    }

    private val statusView = TextView(context).apply {
        visibility = View.GONE
        gravity = Gravity.CENTER
        setTextColor(NativeColors.textSecondary)
        applySheetTypography(BottomSheetTypography.body)
        setPadding(context.dp(24), context.dp(16), context.dp(24), context.dp(24))
        setOnClickListener { if (isClickable) onRetry() }
    }

    private val emptyStateView = LinearLayout(context).apply {
        visibility = View.GONE
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), 0, context.dp(24), 0)
        
        val iconView = com.axoo.engine.emoji.animation.drawable.AnimatedLottieView(context, context.dp(64)).apply {
            setAnimation(
                id = "search_empty",
                path = "axooji/animations/focaccia-peroni/thinking.tgs"
            )
        }

        val titleView = TextView(context).apply {
            gravity = Gravity.CENTER
            applySheetTypography(BottomSheetTypography.title)
            setTextColor(NativeColors.textPrimary)
            text = EMPTY_RESULTS_MESSAGE
        }

        addView(iconView, LinearLayout.LayoutParams(context.dp(64), context.dp(64)).apply { bottomMargin = context.dp(12) })
        addView(titleView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    private val skeletonView = RemoteMediaSkeletonView(context).apply {
        visibility = View.GONE
    }


    private val controlsSection = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            controlsRow,
            LinearLayout.LayoutParams(MATCH_PARENT, context.dp(44)).apply {
                leftMargin = context.dp(20)
                rightMargin = context.dp(20)
                topMargin = context.dp(4)
                bottomMargin = context.dp(4)
            },
        )

    }

    private val mediaGrid = FrameLayout(context).apply {
        addView(recyclerView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(skeletonView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(statusView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(emptyStateView, LayoutParams(MATCH_PARENT, MATCH_PARENT))

    }

    init {
        // Material owns the sheet surface; the search field has its own gray pill.
        setBackgroundColor(NativeColors.pickerSurface)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    controlsSection,
                    LinearLayout.LayoutParams(MATCH_PARENT, context.dp(PICKER_HEADER_HEIGHT_DP)),
                )
                addView(mediaGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            },
            LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
    }

    fun bind(state: RemoteMediaState) {
        personalAdapter.submit(listOf(CreateStickerTile) + state.personalStickers)
        val showAup = state.type == RemoteMediaType.Sticker && state.query.isBlank()
        if (showAup != showingAup) {
            showingAup = showAup
            recyclerView.adapter = if (showAup) stickerSections else remoteAdapter
        }
        searchField.hint = "Search KLIPY"
        if (searchField.text.toString() != state.query) {
            suppressSearchCallback = true
            searchField.setText(state.query)
            searchField.setSelection(searchField.text.length)
            suppressSearchCallback = false
        }
        bindMediaGrid(state)
    }

    private fun bindMediaGrid(state: RemoteMediaState) {
        remoteAdapter.submit(state.items)
        remoteStatus.bind(state)
        val showSkeleton = !showingAup && state.isLoading && state.items.isEmpty()
        val showError = !showSkeleton &&
            state.items.isEmpty() &&
            state.error == RemoteMediaError.LoadingFailed && !showingAup
        val showEmptyState = !showingAup && !showSkeleton && state.items.isEmpty() && !showError
        skeletonView.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        emptyStateView.visibility = if (showEmptyState) View.VISIBLE else View.GONE
        bindStatus(
            visible = showError,
            message = OverlayCopy.mediaNetworkError,
            retryable = showError,
        )
    }

    fun setNavigationBottomInset(bottomInsetPx: Int) {
        val inset = bottomInsetPx.coerceAtLeast(0)
        recyclerView.setAxooPadding(bottom = inset + context.dp(12))

    }

    fun clearSearchFocus() {
        searchField.clearFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        creator?.unregister()
        creator = null
        super.onDetachedFromWindow()
    }

    private fun bindStatus(visible: Boolean, message: String, retryable: Boolean) {
        statusView.visibility = if (visible) View.VISIBLE else View.GONE
        statusView.text = message
        statusView.isClickable = retryable
    }

    private companion object {
        const val PICKER_HEADER_HEIGHT_DP = 52
        const val EMPTY_RESULTS_MESSAGE = OverlayCopy.mediaEmpty
        const val LOAD_MORE_THRESHOLD = 3
        const val MATCH_PARENT = LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = LayoutParams.WRAP_CONTENT
    }
}

private class RemoteMediaSkeletonView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.surface
    }
    private val block = RectF()
    private val rowPatterns = arrayOf(
        floatArrayOf(0.38f, 0.32f, 0.30f),
        floatArrayOf(0.28f, 0.42f, 0.30f),
        floatArrayOf(0.34f, 0.28f, 0.38f),
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = context.dp(2).toFloat()
        val rowHeight = context.dp(104).toFloat()
        val radius = context.dp(3).toFloat()
        var top = 0f
        var row = 0
        while (top < height) {
            val pattern = rowPatterns[row % rowPatterns.size]
            val contentWidth = width - gap * (pattern.size - 1)
            var left = 0f
            pattern.forEachIndexed { index, fraction ->
                val right = if (index == pattern.lastIndex) {
                    width.toFloat()
                } else {
                    left + contentWidth * fraction
                }
                block.set(left, top, right, minOf(top + rowHeight, height.toFloat()))
                canvas.drawRoundRect(block, radius, radius, paint)
                left = right + gap
            }
            top += rowHeight + gap
            row++
        }
    }
}

private class MediaSectionHeader(private val title: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, context.dp(40))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(10))
            textSize = 14f
            typeface = context.baloo2Typeface(800)
            setTextColor(NativeColors.textSecondary)
        }) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder.itemView as TextView).text = title
    }
}

private class RemoteMediaStatusAdapter(private val onRetry: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var state = RemoteMediaState(isLoading = true)
    fun bind(value: RemoteMediaState) {
        if (state == value) return
        val hidden = state.items.isNotEmpty() && value.items.isNotEmpty()
        state = value
        if (hidden) return
        notifyDataSetChanged()
    }
    override fun getItemCount() = if (state.items.isEmpty()) 1 else 0
    override fun getItemViewType(position: Int) = if (state.isLoading) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = if (viewType == 0) RemoteMediaSkeletonView(parent.context) else TextView(parent.context).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(20), context.dp(16), context.dp(20), context.dp(16))
            applySheetTypography(BottomSheetTypography.body)
            setTextColor(NativeColors.textSecondary)
            setOnClickListener { onRetry() }
        }
        view.layoutParams = RecyclerView.LayoutParams(-1, parent.context.dp(if (viewType == 0) 208 else 80))
        return object : RecyclerView.ViewHolder(view) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder.itemView as? TextView)?.apply {
            text = if (state.error != null) OverlayCopy.mediaNetworkError else OverlayCopy.mediaEmpty
            isClickable = state.error != null
        }
    }
}
