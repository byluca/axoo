package com.axoo.features.posts.presentation.composer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.axoo.engine.emoji.catalog.Emoji
import com.axoo.engine.emoji.picker.EmojiPanelDelegate
import com.axoo.engine.emoji.text.EmojiTextEditor

class ComposerController(
    context: Context,
) : EmojiPanelDelegate {
    val panelController = BottomInputPanelController(context)

    private var input: EmojiTextEditor? = null
    private var panelRequestGeneration = 0L

    var changeListener: () -> Unit = {}
    var keyboardAnchor: View? = null

    var state: ComposerState = ComposerState()
        private set

    val pickerOpen: Boolean
        get() = state.panel == ComposerPanelState.Emoji

    fun bindInput(input: EmojiTextEditor) {
        this.input = input
    }

    fun unbindInput(input: EmojiTextEditor) {
        if (this.input === input) {
            this.input = null
        }
    }

    fun toggleEmojiPanel() {
        if (panelController.isSwitchInProgress()) return
        if (pickerOpen) {
            closePanel(focusKeyboardNext = true)
        } else {
            openEmojiPanel()
        }
    }

    fun openEmojiPanel() {
        if (panelController.isSwitchInProgress()) return
        if (beginPanelRequest(ComposerPanelState.Emoji) == null) return
        panelController.openPicker()
        hideInputKeyboard()
    }

    fun closePanel(focusKeyboardNext: Boolean) {
        if (focusKeyboardNext && panelController.isSwitchInProgress()) return
        val target = if (focusKeyboardNext) ComposerPanelState.Keyboard else ComposerPanelState.Hidden
        val generation = beginPanelRequest(target) ?: return
        panelController.closePicker(focusKeyboardNext = focusKeyboardNext)
        if (focusKeyboardNext) requestInputFocus(generation)
    }

    fun requestKeyboard() {
        if (panelController.isSwitchInProgress()) return
        val generation = beginPanelRequest(
            panel = ComposerPanelState.Keyboard,
            retryWhenImeIsNotVisible = true,
        ) ?: return
        panelController.closePicker(focusKeyboardNext = true)
        requestInputFocus(generation)
    }

    /** Switches away from the emoji panel without stealing focus from another text field. */

    fun hideKeyboard() {
        beginPanelRequest(ComposerPanelState.Hidden)
        panelController.closePicker(focusKeyboardNext = false)
        hideSoftKeyboard()
    }

    fun dismissAll() {
        beginPanelRequest(ComposerPanelState.Hidden)
        hideSoftKeyboard()
        panelController.closePicker(focusKeyboardNext = false)
    }

    fun reconcileImeVisibility(visible: Boolean) {
        if (visible && state.panel == ComposerPanelState.Emoji) {
            hideInputKeyboard()
        }
    }

    override fun onEmojiPicked(emoji: Emoji) {
        input?.insertEmoji(emoji.id)
    }

    override fun onBackspace() {
        input?.deleteLastChar()
    }

    private fun beginPanelRequest(
        panel: ComposerPanelState,
        retryWhenImeIsNotVisible: Boolean = false,
    ): Long? {
        val sameTarget = state.panel == panel
        if (sameTarget && !(retryWhenImeIsNotVisible && panelController.mode() == BottomInputPanelController.Mode.None)) {
            return null
        }
        panelRequestGeneration++
        setPanel(panel)
        return panelRequestGeneration
    }

    private fun setPanel(panel: ComposerPanelState) {
        if (state.panel == panel) return
        state = state.copy(panel = panel)
        changeListener()
    }

    private fun hideSoftKeyboard() {
        hideInputKeyboard()
        val anchor = keyboardAnchor ?: return
        val imm = anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager ?: return
        anchor.windowToken?.let { token ->
            imm.hideSoftInputFromWindow(token, 0)
        }
    }

    private fun requestInputFocus(generation: Long) {
        val view = input?.getView() ?: return
        if (!isCurrentKeyboardRequest(generation)) return
        view.requestFocus()
        view.post {
            requestImeUntilVisible(view, generation)
        }
    }

    private fun requestImeUntilVisible(view: View, generation: Long) {
        if (!isCurrentKeyboardRequest(generation)) return
        if (!view.isAttachedToWindow || panelController.isImeVisible()) return
        if (!view.hasFocus()) view.requestFocus()
        view.context.findActivity()?.let { activity ->
            WindowInsetsControllerCompat(activity.window, view)
                .show(WindowInsetsCompat.Type.ime())
        }
        view.context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        view.postDelayed(
            { requestImeUntilVisible(view, generation) },
            KEYBOARD_RETRY_DELAY_MS,
        )
    }

    private fun isCurrentKeyboardRequest(generation: Long): Boolean =
        generation == panelRequestGeneration && state.panel == ComposerPanelState.Keyboard

    private fun hideInputKeyboard() {
        val view = input?.getView() ?: return
        val imm = view.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private companion object {
        const val KEYBOARD_RETRY_DELAY_MS = 100L
    }
}
