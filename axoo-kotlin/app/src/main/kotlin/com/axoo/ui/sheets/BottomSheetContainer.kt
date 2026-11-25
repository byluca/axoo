package com.axoo.ui.sheets

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.axoo.R
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView

/** Ordinary Views inside a Material dialog. Gesture, animation and Back belong to Material. */
@SuppressLint("ViewConstructor")
open class BottomSheetContainer(
    context: Context,
    private val config: BottomSheetConfig = BottomSheetConfig(),
) : FrameLayout(context) {
    internal val dialog = BottomSheetDialog(context,
        if (config.fillsHeight) R.style.Theme_Axoo_MediaBottomSheet else R.style.Theme_Axoo_BottomSheet)
    private val dismissListeners = mutableListOf<() -> Unit>()
    private var closing = false
    private var dismissed = false
    private val initialState: Int
        get() = if (config.fillsHeight) BottomSheetBehavior.STATE_HALF_EXPANDED
        else BottomSheetBehavior.STATE_EXPANDED

    /** Blocks user dismissal during submissions; explicit cleanup can still dismiss the dialog. */
    var allowsDismissal = true
        set(value) {
            field = value
            dialog.setCancelable(value)
            dialog.setCanceledOnTouchOutside(value && config.dismissOnScrimClick)
            dialog.behavior.isHideable = value && config.dragToDismiss
            dialog.behavior.isDraggable = value && config.dragToDismiss
        }

    init {
        dialog.setContentView(this, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (config.fillsHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        dialog.setDismissWithAnimation(true)
        dialog.setOnDismissListener { notifyDismissedOnce() }
        dialog.behavior.apply {
            isFitToContents = !config.fillsHeight
            halfExpandedRatio = 0.5f
            setShouldRemoveExpandedCorners(false)
            skipCollapsed = true
            state = initialState
        }
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            // Material's outer sheet defaults to WRAP_CONTENT, even with MATCH_PARENT content.
            // Media needs a real viewport; otherwise the weighted grid measures almost to zero.
            if (config.fillsHeight) {
                layoutParams = layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            backgroundTintList = ColorStateList.valueOf(config.sheetBackgroundColor)
            clipToOutline = true
        }
        allowsDismissal = true
    }

    fun setSheetContent(content: View) {
        removeAllViews()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (config.showsDragHandle) {
                addView(BottomSheetDragHandleView(dialog.context))
            }
            if (config.fillsHeight) {
                // RecyclerView owns scrolling; do not put a media grid inside a ScrollView.
                addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            } else {
                addView(NestedScrollView(context).apply {
                    overScrollMode = View.OVER_SCROLL_ALWAYS
                    isFillViewport = false
                    addView(content, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    ))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ))
            }
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT,
            if (config.fillsHeight) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT))
    }

    /** Uses the native dialog window instead of mounting another full-screen overlay View. */
    fun presentOn(anchor: View) {
        if (dialog.isShowing || dismissed) return
        val lifecycle = anchor.findViewTreeLifecycleOwner()?.lifecycle
        if (lifecycle?.currentState == Lifecycle.State.DESTROYED) {
            notifyDismissedOnce()
            return
        }
        if (lifecycle != null) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) dismissImmediately()
            }
            lifecycle.addObserver(observer)
            addOnDismissedListener { lifecycle.removeObserver(observer) }
        }
        show()
    }

    internal fun show() {
        if (dialog.isShowing || dismissed) return
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,
            if (config.fillsHeight) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT)
        dialog.show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The dialog has its own window; remove Android's three-button navigation scrim.
            dialog.window?.isNavigationBarContrastEnforced = false
        }
        dialog.behavior.state = initialState
    }

    fun dismiss() {
        if (closing || dismissed) return
        closing = true
        if (dialog.isShowing) {
            // Explicit dismissal is allowed even when interactive dismissal is disabled.
            dialog.behavior.isHideable = true
            // Before the first layout Material cannot animate or emit the hidden-state callback.
            if (isLaidOut) dialog.cancel() else dialog.dismiss()
        } else notifyDismissedOnce()
    }

    fun dismissImmediately() {
        if (dismissed) return
        closing = true
        if (dialog.isShowing) dialog.dismiss() else notifyDismissedOnce()
    }

    protected fun dismissThen(action: () -> Unit) {
        if (closing || dismissed) return
        addOnDismissedListener(action)
        dismiss()
    }

    fun addOnDismissedListener(listener: () -> Unit) {
        if (dismissed) listener() else dismissListeners += listener
    }

    private fun notifyDismissedOnce() {
        if (dismissed) return
        dismissed = true
        val listeners = dismissListeners.toList()
        dismissListeners.clear()
        listeners.forEach { it() }
    }

    protected fun makeTitle(text: String) = android.widget.TextView(context).apply {
        this.text = text
        setTextColor(NativeColors.textPrimary)
        applySheetTypography(BottomSheetTypography.title)
    }

    protected fun makeBody(text: String) = android.widget.TextView(context).apply {
        this.text = text
        setTextColor(NativeColors.textSecondary)
        applySheetTypography(BottomSheetTypography.body)
    }

    protected fun makeActionText(text: String, destructive: Boolean = false) =
        android.widget.TextView(context).apply {
            this.text = text
            setTextColor(if (destructive) NativeColors.destructive else NativeColors.textPrimary)
            applySheetTypography(BottomSheetTypography.action)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
        }
}
