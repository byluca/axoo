package com.axoo.features.profile.presentation.nickname

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.R
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.profile.presentation.NicknameState
import com.axoo.features.profile.presentation.NicknameStatus
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.AxooTextInput
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class NicknameEditorHost(
    context: Context,
    private val viewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.NicknameEditor

    private val view = NicknameEditorView(context)
    private var observerJob: Job? = null

    init {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        view.setCallbacks(
            onBack = ::close,
            onNicknameChanged = viewModel::changeNickname,
            onSave = viewModel::saveNickname,
        )
    }

    override fun onScreenFullyVisible() {
        if (observerJob == null) {
            viewModel.beginNicknameEditing()
            observerJob = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.nickname.collect { state ->
                        view.render(state)
                        if (state.status == NicknameStatus.Saved) close()
                    }
                }
            }
        }
        view.focusInput()
    }

    override fun onScreenHidden() {
        observerJob?.cancel()
        observerJob = null
        viewModel.stopNicknameEditing()
        view.hideKeyboard()
    }

    override fun onInteractiveTransitionStart(isOpening: Boolean) {
        if (!isOpening) view.hideKeyboard()
    }

    override fun onScreenDestroyed() = onScreenHidden()

    override fun onBackPressed(): Boolean {
        close()
        return true
    }

    private fun close() {
        view.hideKeyboard()
        onBack()
    }
}

private class NicknameEditorView(context: Context) : FrameLayout(context) {
    private var onBack: () -> Unit = {}
    private var onNicknameChanged: (String) -> Unit = {}
    private var onSave: () -> Unit = {}
    private var statusTopInset = 0
    private var navigationBottomInset = 0
    private var rendering = false

    private val topBar = FrameLayout(context).apply { setBackgroundColor(NativeColors.card) }
    private val back = ArrowBackButton(context)
    private val save = MaterialButton(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)).apply {
        text = "Salva"
        textSize = 15f
        typeface = context.baloo2Typeface(700)
        includeFontPadding = false
        isAllCaps = false
        letterSpacing = 0f
        setTextColor(NativeColors.onAccent)
        backgroundTintList = ColorStateList.valueOf(NativeColors.accent)
        cornerRadius = dp(16)
        insetTop = 0
        insetBottom = 0
        minimumWidth = 0
        minWidth = 0
        minimumHeight = 0
        minHeight = 0
        elevation = 0f
        stateListAnimator = null
        setPadding(dp(14), 0, dp(14), 0)
        contentDescription = "Salva"
    }
    private val input = AxooTextInput(context).apply {
        hint = "Nickname"
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        // La card esterna è già la pill condivisa: il campo resta piatto per
        // evitare una seconda pill grigia dentro la superficie bianca.
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        minimumHeight = dp(44)
        setPadding(0, 0, 0, 0)
    }
    private val status = TextView(context).apply {
        textSize = 14f
        typeface = context.interTypeface(400)
        includeFontPadding = false
    }
    private val instructions = TextView(context).apply {
        text = "Da 3 a 24 caratteri. Usa solo lettere minuscole e numeri, senza spazi."
        textSize = 14f
        typeface = context.interTypeface(400)
        includeFontPadding = false
        setTextColor(NativeColors.textSecondary)
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    private val scroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        isFillViewport = true
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    init {
        setBackgroundColor(NativeColors.surface)
        buildTopBar()
        buildContent()

        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(44)
        })

        back.setOnClickListener { onBack() }
        save.setOnClickListener { if (save.isEnabled) onSave() }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rendering) onNicknameChanged(text?.toString().orEmpty())
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && save.isEnabled) {
                onSave()
                true
            } else {
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navigationBottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (topBar.layoutParams as LayoutParams).height = dp(44) + statusTopInset
            (scroll.layoutParams as LayoutParams).topMargin = dp(44) + statusTopInset
            content.setPadding(dp(24), dp(24), dp(24), dp(24) + navigationBottomInset)
            requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(this)
        updateSaveEnabled(false)
    }

    private fun buildTopBar() {
        back.contentDescription = "Indietro"
        topBar.addView(back, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = dp(6)
        })
        topBar.addView(TextView(context).apply {
            text = "Modifica nickname"
            textSize = 22f
            typeface = context.baloo2Typeface(700)
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(NativeColors.textPrimary)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.BOTTOM).apply {
            leftMargin = dp(54)
            rightMargin = dp(94)
        })
        topBar.addView(save, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36), Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(16)
            bottomMargin = dp(4)
        })
    }

    private fun buildContent() {
        content.addView(NativePillCard(context).apply {
            // Match the iOS field card without the shared card's elevation.
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
            val form = LinearLayout(context).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            addView(form, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24)
        })
        content.addView(instructions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24)
        })
    }

    fun setCallbacks(
        onBack: () -> Unit,
        onNicknameChanged: (String) -> Unit,
        onSave: () -> Unit,
    ) {
        this.onBack = onBack
        this.onNicknameChanged = onNicknameChanged
        this.onSave = onSave
    }

    fun focusInput() {
        input.post {
            input.requestFocus()
            ViewCompat.getWindowInsetsController(input)?.show(WindowInsetsCompat.Type.ime())
        }
    }

    fun hideKeyboard() {
        input.clearFocus()
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
    }

    fun render(state: NicknameState) {
        if (input.text?.toString()?.trim() != state.value) {
            rendering = true
            input.setText(state.value)
            input.setSelection(input.length())
            rendering = false
        }
        input.isEnabled = state.status != NicknameStatus.Saving
        status.text = state.message
        status.setTextColor(when (state.status) {
            NicknameStatus.Available -> NativeColors.accent
            NicknameStatus.Invalid -> NativeColors.destructive
            else -> NativeColors.textSecondary
        })
        updateSaveEnabled(state.canSave)
    }

    private fun updateSaveEnabled(enabled: Boolean) {
        save.isEnabled = enabled
        save.alpha = if (enabled) 1f else 0.5f
    }
}
