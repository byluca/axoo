package com.axoo.features.profile.presentation.bio

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.EmojiTextEditor
import com.axoo.features.posts.presentation.composer.BottomInputPanelController
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.composer.ComposerEmojiPanelHostView
import com.axoo.features.profile.presentation.MAX_BIO_LENGTH
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativeEmojiKeyboardToggleView
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.NativePillStyle
import com.axoo.ui.components.defaultPickerPanelHeight
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class BioEditorHost(
    context: Context,
    private val viewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
) : LinearLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.BioEditor
    private var observer: Job? = null
    private var rendering = false
    private var screenVisible = false
    private val composer = ComposerController(context)
    private val panel = composer.panelController
    private val input = EmojiTextEditor(
        context, emojis = EmojiData.emojis, fontFamily = "Inter", fontSizeSp = 16f,
        hintText = "Qualcosa su di te…", maxLines = 8, verticalPaddingPx = dp(10),
        lineSpacingMultiplier = 1.12f,
        onTextChanged = { text, entities -> if (!rendering) viewModel.changeBio(text, entities) },
    )
    private val inputView = (input.getView() as TextView).apply {
        contentDescription = "Bio"
        setHintTextColor(NativeColors.textSecondary)
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) composer.requestKeyboard()
            false
        }
    }
    private val toggle = NativeEmojiKeyboardToggleView(context).apply {
        setOnClickListener { if (isEnabled) { input.finishComposition(); composer.toggleEmojiPanel() } }
    }
    private val picker = ComposerEmojiPanelHostView(context, composer, supportsMedia = false)
    private val status = TextView(context).apply {
        textSize = 14f
        typeface = context.interTypeface(400)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val save = NativeLoadingButton(context, textSizeSp = 15f).apply {
        setOnClickListener { input.finishComposition(); viewModel.saveBio() }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(NativeColors.surface)
        composer.bindInput(input)
        composer.keyboardAnchor = this
        composer.changeListener = ::updatePanel
        panel.listener = object : BottomInputPanelController.Listener {
            override fun onPanelStateChanged(targetBottom: Int, mode: BottomInputPanelController.Mode) = updatePanel()
        }
        val bar = FrameLayout(context).apply {
            setBackgroundColor(NativeColors.card)
            addView(ArrowBackButton(context).apply { setOnClickListener { close() } },
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(TextView(context).apply {
                text = "Modifica bio"
                textSize = 22f
                typeface = context.baloo2Typeface(800)
                setTextColor(NativeColors.textPrimary)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
                leftMargin = dp(48)
                rightMargin = dp(88)
            })
            addView(save, FrameLayout.LayoutParams(dp(80), dp(40), Gravity.END or Gravity.CENTER_VERTICAL))
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            clipChildren = false
            addView(TextView(context).apply {
                text = "La bio è facoltativa e visibile agli altri utenti. Racconta qualcosa di te, mantenendo il tuo anonimato."
                textSize = 14f
                typeface = context.interTypeface(400)
                setTextColor(NativeColors.textSecondary)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(NativePillCard(context, NativePillStyle(
                cornerRadiusDp = NativePillDefaults.controlCornerRadiusDp.toFloat(),
            )).apply {
                orientation = HORIZONTAL
                gravity = Gravity.TOP
                minimumHeight = dp(120)
                setPadding(dp(4), dp(2), dp(8), dp(2))
                setOnClickListener { if (inputView.isEnabled) composer.requestKeyboard() }
                addView(toggle, LayoutParams(dp(40), dp(40)).apply {
                    gravity = Gravity.TOP
                })
                addView(FrameLayout(context).apply {
                    setPadding(dp(12), 0, dp(8), 0)
                    addView(inputView, FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP,
                    ))
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            })
        }
        addView(ScrollView(context).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(content)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, context.defaultPickerPanelHeight()))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            bar.setPadding(dp(6), insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, dp(6), 0)
            picker.setNavigationBottomInset(nav)
            panel.updateInsets(nav, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                insets.isVisible(WindowInsetsCompat.Type.ime()),
                width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels, resources.configuration.orientation)
            composer.reconcileImeVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        input.setActive(false)
        updatePanel()
    }

    private fun updatePanel() {
        val show = panel.shouldShowPicker()
        val active = screenVisible && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        picker.visibility = if (show) VISIBLE else GONE
        if (show) {
            val height = panel.pickerPanelHeight(context.defaultPickerPanelHeight())
            if (picker.layoutParams.height != height) picker.layoutParams = picker.layoutParams.apply { this.height = height }
        }
        picker.setEmojiPlaybackGate(if (active) EmojiPlaybackGate.FullyVisible else EmojiPlaybackGate.Inactive)
        picker.setActive(show && active && inputView.isEnabled)
        setPadding(0, 0, 0, if (show) 0 else panel.targetBottomOffset(context.defaultPickerPanelHeight()))
        toggle.contentDescription = if (composer.pickerOpen) "Apri tastiera" else "Apri emoji"
        toggle.bind(composer.pickerOpen, NativeColors.textSecondary, dp(28f), dp(40f))
    }

    override fun onScreenFullyVisible() {
        screenVisible = true
        input.setEmojiPlaybackGate(EmojiPlaybackGate.FullyVisible)
        ViewCompat.requestApplyInsets(this)
        if (observer != null) return
        viewModel.beginBioEditing()
        observer = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    input.setActive(true)
                    updatePanel()
                    viewModel.bio.collect { state ->
                        rendering = true
                        input.setTextValue(state.value, state.entities)
                        rendering = false
                        inputView.isEnabled = !state.saving && state.original != null
                        toggle.isEnabled = inputView.isEnabled
                        if (state.saving) composer.dismissAll()
                        save.bind("Salva", state.canSave, state.saving)
                        status.text = state.error ?: "${state.length}/$MAX_BIO_LENGTH"
                        status.setTextColor(if (state.error != null || state.length > MAX_BIO_LENGTH)
                            NativeColors.destructive else NativeColors.textSecondary)
                        if (state.saved) close()
                    }
                } finally {
                    input.setActive(false)
                    picker.setActive(false)
                }
            }
        }
        inputView.post { if (screenVisible) { input.moveCursorToEnd(); composer.requestKeyboard() } }
    }

    private fun close() { composer.dismissAll(); onBack() }
    override fun onBackPressed(): Boolean {
        if (composer.pickerOpen) composer.closePanel(focusKeyboardNext = false) else close()
        return true
    }
    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        input.setEmojiPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
        picker.setEmojiPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
        if (!isOpening) composer.dismissAll()
    }
    override fun onScreenHidden() {
        screenVisible = false
        observer?.cancel()
        observer = null
        viewModel.stopBioEditing()
        composer.dismissAll()
        input.setActive(false)
        picker.setActive(false)
    }
    override fun onScreenDestroyed() {
        onScreenHidden()
        composer.unbindInput(input)
        composer.changeListener = {}
        composer.keyboardAnchor = null
        panel.listener = null
        input.dispose()
        picker.dispose()
    }
}
