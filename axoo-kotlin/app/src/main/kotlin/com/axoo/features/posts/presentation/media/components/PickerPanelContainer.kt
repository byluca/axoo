package com.axoo.features.posts.presentation.media.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.axoo.core.anim.BottomSheetAnimator

/**
 * Shared shell for the fixed picker shown below the create-post toolbar.
 *
 * Only the embedded emoji keyboard uses this shell. GIF/Meme use Material dialogs.
 */
@SuppressLint("ViewConstructor")
internal class PickerPanelContainer(context: Context) : FrameLayout(context) {
    private val animator = BottomSheetAnimator()
    private var activeContent: View? = null
    private var expandedHeight = 0
    private var onContentShown: () -> Unit = {}

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false
    }

    fun show(
        content: View,
        height: Int,
        collapsedHeight: Int,
        animateFromCollapsed: Boolean,
        onShown: () -> Unit = {},
    ) {
        require(content.parent === this)
        expandedHeight = height
        onContentShown = onShown
        val wasVisible = isVisible
        activeContent = content
        content.visibility = VISIBLE
        translationY = 0f

        if (!wasVisible) {
            animator.cancel()
            setHeight(if (animateFromCollapsed) collapsedHeight else height)
            visibility = VISIBLE
            if (!animateFromCollapsed) {
                if (activeContent === content) onShown()
                return
            }
        }

        animator.showHeight(this, height) {
            if (activeContent === content) onShown()
        }
    }

    fun hide(
        fullHeight: Int,
        collapsedHeight: Int,
        onHidden: () -> Unit = {},
    ) {
        activeContent = null
        if (!isVisible) return

        animator.hideHeight(this, fullHeight, collapsedHeight) {
            if (activeContent != null) return@hideHeight
            hideChildren()
            visibility = GONE
            translationY = 0f
            setHeight(0)
            onHidden()
        }
    }

    fun hideImmediately() {
        activeContent = null
        animator.cancel()
        hideChildren()
        visibility = GONE
        translationY = 0f
        setHeight(0)
    }

    fun updateExpandedHeight(height: Int) {
        if (activeContent == null || expandedHeight == height) return
        expandedHeight = height
        animator.cancel()
        setHeight(height)
        onContentShown()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        forEachChild { it.animate().cancel() }
        super.onDetachedFromWindow()
    }

    private fun hideChildren() {
        forEachChild { child ->
            child.animate().cancel()
            child.alpha = 1f
            child.visibility = GONE
        }
    }

    private inline fun forEachChild(block: (View) -> Unit) {
        for (index in 0 until childCount) block(getChildAt(index))
    }

    private fun setHeight(value: Int) {
        val params = layoutParams ?: return
        val height = value.coerceAtLeast(0)
        if (params.height == height) return
        params.height = height
        layoutParams = params
    }

}
