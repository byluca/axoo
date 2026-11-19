package com.axoo.features.posts.presentation.composer

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

internal enum class InputPanelTarget { None, Keyboard, Emoji }

internal fun isUsableImePanelHeight(heightPx: Int, navInsetPx: Int, minimumPx: Int): Boolean =
    heightPx > navInsetPx && heightPx >= minimumPx

internal fun stableImePanelHeight(savedHeightPx: Int, candidateHeightPx: Int): Int = when {
    savedHeightPx <= 0 -> candidateHeightPx
    candidateHeightPx <= 0 -> savedHeightPx
    else -> minOf(savedHeightPx, candidateHeightPx)
}

internal data class InputPanelIntent(
    val target: InputPanelTarget = InputPanelTarget.None,
    val awaitingKeyboard: Boolean = false,
) {
    fun request(next: InputPanelTarget): InputPanelIntent {
        if (target == next) return this
        return InputPanelIntent(
            target = next,
            awaitingKeyboard = next == InputPanelTarget.Keyboard &&
                target == InputPanelTarget.Emoji,
        )
    }

    fun settleIme(imeVisible: Boolean): InputPanelIntent {
        if (!awaitingKeyboard || target != InputPanelTarget.Keyboard || !imeVisible) return this
        return copy(awaitingKeyboard = false)
    }

    fun isSwitchInProgress(imeVisible: Boolean, imeAnimating: Boolean): Boolean =
        imeAnimating || awaitingKeyboard || target == InputPanelTarget.Emoji && imeVisible
}

/**
 * State machine for the bottom input area: navigation inset + IME + emoji picker.
 *
 * Goal: emoji picker height is always equal to the last known IME height so that
 * switching between keyboard and picker never resizes the composer (Telegram style).
 *
 * The baseline IME height is persisted per geometry bucket. Temporary expansions
 * inside a keyboard (emoji/GIF search) must not make the app picker taller.
 */
class BottomInputPanelController(context: Context) {

    enum class Mode { None, Keyboard, Emoji }

    interface Listener {
        fun onPanelStateChanged(targetBottom: Int, mode: Mode)
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private val minimumStableImeHeightPx = (MINIMUM_STABLE_IME_HEIGHT_DP * density).roundToInt()

    var listener: Listener? = null

    private var navBottomInset = 0
    private var imeBottomInset = 0
    private var imeVisible = false
    private var lastKnownImeHeight = 0
    private var intent = InputPanelIntent()
    private var geometryKey: String = ""
    private var imeAnimationDepth = 0

    fun updateInsets(
        nav: Int,
        ime: Int,
        imeVisible: Boolean = ime > nav,
        widthPx: Int,
        orientation: Int,
    ) {
        navBottomInset = nav
        imeBottomInset = ime
        this.imeVisible = imeVisible

        val key = geometryKey(widthPx, orientation, nav > 0)
        if (key != geometryKey) {
            geometryKey = key
            val storedHeight = prefs.getInt(key, 0)
            lastKnownImeHeight = storedHeight.takeIf(::isUsableImeHeight) ?: 0
            if (storedHeight > 0 && lastKnownImeHeight == 0) {
                prefs.edit().remove(key).apply()
            }
        }

        rememberCurrentImeHeight()
        if (imeAnimationDepth == 0) {
            intent = intent.settleIme(imeVisible)
        }
        emit()
    }

    fun openPicker() {
        val next = intent.request(InputPanelTarget.Emoji)
        if (next == intent) return
        rememberCurrentImeHeight()
        intent = next
        emit()
    }

    fun closePicker(focusKeyboardNext: Boolean) {
        val target = if (focusKeyboardNext) InputPanelTarget.Keyboard else InputPanelTarget.None
        val next = intent.request(target)
        if (next == intent) return
        intent = next
        emit()
    }

    fun mode(): Mode = when {
        intent.target == InputPanelTarget.Emoji -> Mode.Emoji
        imeBottomInset > navBottomInset || intent.awaitingKeyboard -> Mode.Keyboard
        else -> Mode.None
    }

    fun pickerOpen(): Boolean = intent.target == InputPanelTarget.Emoji

    fun shouldShowPicker(): Boolean = pickerOpen() || intent.awaitingKeyboard

    fun isImeVisible(): Boolean = imeVisible

    fun isSwitchInProgress(): Boolean =
        intent.isSwitchInProgress(
            imeVisible = imeVisible,
            imeAnimating = imeAnimationDepth > 0,
        )

    fun onImeAnimationStarted() {
        imeAnimationDepth++
    }

    fun resetImeAnimationState() {
        imeAnimationDepth = 0
    }

    fun onImeAnimationEnd() {
        if (imeAnimationDepth > 0) imeAnimationDepth--
        if (imeAnimationDepth > 0) return
        rememberCurrentImeHeight()
        val next = intent.settleIme(isImeVisible())
        intent = next
        emit()
    }

    /** Uses the stable baseline, never a temporary expanded keyboard height. */
    fun pickerPanelHeight(fallbackPx: Int): Int = when {
        lastKnownImeHeight > 0 -> lastKnownImeHeight
        imeAnimationDepth == 0 && imeVisible && isUsableImeHeight(imeBottomInset) -> imeBottomInset
        else -> fallbackPx
    }

    fun navBottomInset(): Int = navBottomInset

    /**
     * Vertical clearance the composer should sit on top of.
     *
     * - picker open: picker height
     * - keyboard up: ime inset
     * - awaiting keyboard: last known ime height, so the bar does not drop and snap up
     * - otherwise: nav inset
     */
    fun targetBottomOffset(fallbackPickerPx: Int): Int = when {
        pickerOpen() -> pickerPanelHeight(fallbackPickerPx)
        intent.awaitingKeyboard && lastKnownImeHeight > 0 -> lastKnownImeHeight
        imeBottomInset > navBottomInset -> imeBottomInset
        else -> navBottomInset
    }

    private fun emit() {
        val l = listener ?: return
        l.onPanelStateChanged(
            targetBottom = targetBottomOffset(fallbackPickerPx = 0),
            mode = mode(),
        )
    }

    private fun rememberCurrentImeHeight() {
        if (
            geometryKey.isEmpty() ||
            imeAnimationDepth > 0 ||
            !imeVisible ||
            !isUsableImeHeight(imeBottomInset)
        ) return
        val baseline = stableImePanelHeight(lastKnownImeHeight, imeBottomInset)
        if (baseline == lastKnownImeHeight) return
        lastKnownImeHeight = baseline
        prefs.edit().putInt(geometryKey, baseline).apply()
    }

    private fun isUsableImeHeight(heightPx: Int): Boolean =
        isUsableImePanelHeight(heightPx, navBottomInset, minimumStableImeHeightPx)

    private fun geometryKey(widthPx: Int, orientation: Int, hasNav: Boolean): String {
        val widthBucket = (widthPx / 8) * 8
        return "ime_v2_${widthBucket}_${orientation}_${if (hasNav) 1 else 0}"
    }

    private companion object {
        const val PREFS = "axoo_input_panel"
        const val MINIMUM_STABLE_IME_HEIGHT_DP = 180f
    }
}
