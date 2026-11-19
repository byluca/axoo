package com.axoo.features.profile.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.application.navigation.route.BottomTab
import com.axoo.features.profile.presentation.ProfileEvent
import com.axoo.features.profile.presentation.ProfileUiState
import com.axoo.ui.components.BottomFadeView
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativeProfileRootView(context: Context) : FrameLayout(context) {
    private var eventSink: (ProfileEvent) -> Unit = {}
    private var navigationBottomInset: Int = 0
    private var lastRenderedState: ProfileUiState? = null
    private var bioActive = false

    fun setBioActive(active: Boolean) {
        bioActive = active
        for (i in 0 until content.childCount) (content.getChildAt(i) as? ProfileBioView)?.setActive(active)
    }
    private val topBar = NativeProfileTopBar(context)
    private val scroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        clipToPadding = false
        isFillViewport = true
        setBackgroundColor(NativeColors.surface)
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(NativeColors.surface)
    }
    private val bottomBar = NativeProfileBottomBar(context)
    private val bottomFade = BottomFadeView(context)

    init {
        setBackgroundColor(NativeColors.surface)
        clipChildren = false

        topBar.setOnMenu { eventSink(ProfileEvent.MenuTapped) }
        bottomBar.setOnTabSelected { eventSink(ProfileEvent.BottomTabSelected(it)) }

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bottomFade, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            topBar.setStatusTopInset(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (navigationBottomInset != navigationBottom) {
                navigationBottomInset = navigationBottom
                bottomBar.setNavigationBottomInset(navigationBottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { ViewCompat.requestApplyInsets(this) }
    }

    fun setEventSink(callback: (ProfileEvent) -> Unit) {
        eventSink = callback
    }

    fun bindNotificationsCount(count: Int) = bottomBar.bindUnreadCount(count)

    fun render(uiState: ProfileUiState) {
        setBackgroundColor(NativeColors.surface)
        topBar.render()
        bottomBar.bind(BottomTab.PROFILE)
        if (shouldRenderProfileContent(lastRenderedState, uiState)) {
            renderProfileContent(
                content = content,
                uiState = uiState,
                onAvatarTapped = { eventSink(ProfileEvent.AvatarTapped) },
                onNicknameTapped = { eventSink(ProfileEvent.NicknameTapped) },
                onMyPostsTapped = { eventSink(ProfileEvent.MyPostsTapped) },
                onBioTapped = { eventSink(ProfileEvent.BioTapped) },
            )
        }
        lastRenderedState = uiState
        setBioActive(bioActive)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val atMostHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)

        topBar.measure(exactWidth, atMostHeight)
        bottomBar.measure(exactWidth, atMostHeight)

        scroll.setPadding(0, 0, 0, bottomBar.measuredHeight + dp(16))
        val scrollHeight = (height - topBar.measuredHeight).coerceAtLeast(0)
        scroll.measure(exactWidth, MeasureSpec.makeMeasureSpec(scrollHeight, MeasureSpec.EXACTLY))
        bottomFade.measure(exactWidth, MeasureSpec.makeMeasureSpec(
            (bottomBar.measuredHeight + bottomFade.fadeHeightPx).coerceAtMost(scrollHeight),
            MeasureSpec.EXACTLY,
        ))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        var y = 0

        topBar.layout(0, y, width, y + topBar.measuredHeight)
        y += topBar.measuredHeight

        scroll.layout(0, y, width, height)
        bottomFade.layout(0, height - bottomFade.measuredHeight, width, height)
        bottomBar.layout(0, height - bottomBar.measuredHeight, width, height)
    }
}

internal fun shouldRenderProfileContent(
    previous: ProfileUiState?,
    next: ProfileUiState,
): Boolean = previous == null ||
    previous.user != next.user ||
    previous.isLoading != next.isLoading ||
    previous.isError != next.isError
