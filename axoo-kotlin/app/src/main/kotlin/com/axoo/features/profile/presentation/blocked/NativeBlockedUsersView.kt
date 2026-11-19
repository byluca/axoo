package com.axoo.features.profile.presentation.blocked

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.BlockedUsersState
import com.axoo.ui.state.UiState
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.components.NativePillStyle
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativeBlockedUsersView(context: Context) : FrameLayout(context) {
    private var statusTopInset = 0
    private var navigationBottomInset = 0
    private var users: List<User> = emptyList()
    private var onBack: () -> Unit = {}
    private var onUserMenu: (User) -> Unit = {}

    private val topBar = FrameLayout(context).apply { setBackgroundColor(NativeColors.card) }
    private val back = ArrowBackButton(context).apply { setOnClickListener { onBack() } }
    private val title = TextView(context).apply {
        text = "Utenti bloccati"
        textSize = 22f
        typeface = context.baloo2Typeface(800)
        setTextColor(NativeColors.textPrimary)
        includeFontPadding = false
        gravity = Gravity.CENTER
    }
    private val scroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        clipToPadding = false
        isFillViewport = true
        setBackgroundColor(NativeColors.surface)
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(20), dp(16), dp(32))
    }

    init {
        setBackgroundColor(NativeColors.surface)
        topBar.addView(back, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = dp(6)
        })
        topBar.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.BOTTOM).apply {
            leftMargin = dp(54)
            rightMargin = dp(54)
        })
        scroll.addView(content, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderLoading()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navigationBottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            content.setPadding(dp(16), dp(20), dp(16), dp(32) + navigationBottomInset)
            requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setCallbacks(onBack: () -> Unit, onUserMenu: (User) -> Unit) {
        this.onBack = onBack
        this.onUserMenu = onUserMenu
    }

    fun render(state: BlockedUsersState) {
        when (val users = state.users) {
            UiState.Loading -> renderLoading()
            is UiState.Error -> renderError()
            is UiState.Success -> {
                renderUsers(users.data)
                if (state.operationFailed) showOperationError()
            }
        }
    }

    private fun renderLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(statusText("Caricamento…"), cardParams())
    }

    private fun renderUsers(value: List<User>) {
        users = value
        content.removeAllViews()
        content.gravity = if (users.isEmpty()) Gravity.CENTER else Gravity.TOP
        if (users.isEmpty()) {
            content.addView(statusText("Nessun utente bloccato"))
            return
        }
        content.addView(usersCard())
    }

    private fun renderError() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(statusText("Impossibile caricare gli utenti bloccati."))
    }

    private fun showOperationError() {
        // Manteniamo la lista visibile: l'utente può semplicemente riprovare dal menu.
        content.removeAllViews()
        content.gravity = if (users.isEmpty()) Gravity.CENTER else Gravity.TOP
        content.addView(statusText("Operazione non riuscita. Riprova."))
        if (users.isNotEmpty()) content.addView(usersCard(), cardParams().apply { topMargin = dp(12) })
    }

    private fun usersCard(): LinearLayout = NativePillCard(
        context,
        NativePillStyle(cornerRadiusDp = 20f, dividerHorizontalInsetDp = 16),
    ).apply {
        users.forEach { user ->
            addPillRow(userRow(user))
        }
    }

    private fun userRow(user: User): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(8), 0)
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply {
            text = user.nickname
            textSize = 17f
            typeface = context.baloo2Typeface(400)
            setTextColor(NativeColors.textPrimary)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        })
        user.universityName?.takeIf { it.isNotBlank() }?.let { university ->
            details.addView(TextView(context).apply {
                text = university
                textSize = 12f
                typeface = context.baloo2Typeface(400)
                setTextColor(NativeColors.textSecondary)
                includeFontPadding = false
            })
        }
        addView(details, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            rotation = 90f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            imageTintList = ColorStateList.valueOf(NativeColors.textSecondary)
            load("file:///android_asset/icons/dots-three.svg")
            setOnClickListener { onUserMenu(user) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun statusText(message: String): TextView = TextView(context).apply {
        text = message
        textSize = 17f
        typeface = context.baloo2Typeface(400)
        setTextColor(NativeColors.textSecondary)
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = dp(44)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun cardParams() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val topBarHeight = dp(44) + statusTopInset
        topBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(topBarHeight, MeasureSpec.EXACTLY))
        scroll.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((height - topBarHeight).coerceAtLeast(0), MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        topBar.layout(0, 0, width, topBar.measuredHeight)
        scroll.layout(0, topBar.measuredHeight, width, bottom - top)
    }
}
