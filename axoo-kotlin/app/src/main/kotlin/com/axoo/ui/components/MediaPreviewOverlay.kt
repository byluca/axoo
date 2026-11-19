package com.axoo.ui.components

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import coil.dispose
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.axoo.R
import com.axoo.core.haptics.Haptics
import com.axoo.ui.theme.dp
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Hold to inspect, slide across items, release to dismiss. A normal tap stays with the picker. */
internal object MediaPreviewOverlay {
    private val bindings = WeakHashMap<View, Binding>()
    private var active: Session? = null
    val isShowing: Boolean get() = active != null

    fun bind(source: View, key: String, aspectRatio: Float = 1f, maximumSizeDp: Int = 360,
             onVisibility: (Boolean) -> Unit = {}, content: (Context) -> View) {
        clear(source)
        val binding = Binding(source, key, aspectRatio, maximumSizeDp, onVisibility, content)
        source.setTag(R.id.media_preview_binding, binding)
        source.addOnAttachStateChangeListener(binding)
        if (source.isAttachedToWindow) bindings[source] = binding
        source.setOnLongClickListener { binding.open(); true }
        source.setOnTouchListener(binding)
    }

    fun clear(source: View) {
        val previous = source.getTag(R.id.media_preview_binding) as? Binding ?: return
        source.setTag(R.id.media_preview_binding, null)
        bindings.remove(source)
        source.removeOnAttachStateChangeListener(previous)
        source.setOnLongClickListener(null)
        source.isLongClickable = false
        source.setOnTouchListener(null)
        if (active?.owner === previous) dismiss()
    }

    fun dismiss() {
        val session = active ?: return
        active = null
        session.owner.source.get()?.let { it.parent?.requestDisallowInterceptTouchEvent(false); it.isPressed = false }
        session.surface.releaseImages()
        (session.surface.parent as? ViewGroup)?.removeView(session.surface)
        session.owner.onVisibility(false)
    }

    private class Binding(view: View, val key: String, val aspect: Float, val maximumSizeDp: Int,
                          val onVisibility: (Boolean) -> Unit, val content: (Context) -> View) :
        View.OnTouchListener, View.OnAttachStateChangeListener {
        val source = WeakReference(view)
        private var previewed = false

        fun open() {
            val view = source.get() ?: return
            val root = view.rootView as? ViewGroup ?: return
            if (!view.isAttachedToWindow || !view.isShown) return
            dismiss()
            previewed = true
            view.isPressed = false
            view.parent?.requestDisallowInterceptTouchEvent(true)
            Haptics.confirm(view)
            val surface = Surface(view.context)
            active = Session(this, surface, key)
            surface.show(this)
            root.addView(surface, ViewGroup.LayoutParams(-1, -1))
            surface.animateEntrance()
            onVisibility(true)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> previewed = false
                MotionEvent.ACTION_MOVE -> if (previewed) {
                    val next = bindings.entries.firstOrNull { (candidate, _) ->
                        if (candidate.rootView !== view.rootView || !candidate.isShown) false else {
                            val location = IntArray(2); candidate.getLocationOnScreen(location)
                            event.rawX >= location[0] && event.rawX < location[0] + candidate.width &&
                                event.rawY >= location[1] && event.rawY < location[1] + candidate.height
                        }
                    }?.value
                    val session = active
                    if (next != null && session?.owner === this && session.key != next.key) {
                        session.key = next.key
                        session.surface.show(next)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    val consumed = previewed
                    if (active?.owner === this) dismiss()
                    previewed = false
                    return consumed
                }
            }
            return previewed
        }

        override fun onViewAttachedToWindow(view: View) { bindings[view] = this }
        override fun onViewDetachedFromWindow(view: View) {
            bindings.remove(view)
            if (active?.owner === this) dismiss()
        }
    }

    private data class Session(val owner: Binding, val surface: Surface, var key: String)

    private class Surface(context: Context) : FrameLayout(context) {
        private val card = FrameLayout(context).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT); cornerRadius = dp(24).toFloat() }
            clipToOutline = true
        }
        private var aspect = 1f
        private var maximumSizeDp = 360
        private val visible = Rect()
        private val origin = IntArray(2)

        init {
            tag = "media-hold-preview"
            contentDescription = "Anteprima. Tocca per chiudere"
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { dismiss() }
            addView(card)
        }
        fun show(binding: Binding) {
            maximumSizeDp = binding.maximumSizeDp
            aspect = binding.aspect.takeIf { it.isFinite() && it > 0 } ?: 1f
            releaseImages()
            card.removeAllViews()
            val content = binding.content(context).apply {
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                    }
                }
                clipToOutline = true
            }
            card.addView(content, LayoutParams(-1, -1))
            requestLayout()
        }
        fun animateEntrance() {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) return
            alpha = 0f
            card.scaleX = 0.88f; card.scaleY = 0.88f
            animate().alpha(1f).setDuration(160).start()
            card.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        fun releaseImages() {
            for (i in 0 until card.childCount) {
                (card.getChildAt(i) as? ImageView)?.let { image ->
                    (image.drawable as? Animatable)?.stop()
                    image.dispose()
                    image.setImageDrawable(null)
                }
            }
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            getWindowVisibleDisplayFrame(visible)
            val availableHeight = visible.height().takeIf { it > 0 } ?: height
            val maxWidth = (minOf(width - dp(48), dp(maximumSizeDp)) - dp(32)).coerceAtLeast(1)
            val maxHeight = (minOf(availableHeight - dp(96), dp(520)) - dp(32)).coerceAtLeast(1)
            val contentWidth = minOf(maxWidth.toFloat(), maxHeight * aspect).toInt().coerceAtLeast(1)
            val contentHeight = (contentWidth / aspect).toInt().coerceAtLeast(1)
            card.measure(MeasureSpec.makeMeasureSpec(contentWidth + dp(32), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(contentHeight + dp(32), MeasureSpec.EXACTLY))
            setMeasuredDimension(width, height)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            getLocationOnScreen(origin)
            val centerY = if (visible.height() > 0) visible.centerY() - origin[1] else height / 2
            val x = (width - card.measuredWidth) / 2
            val y = (centerY - card.measuredHeight / 2).coerceAtLeast(0)
            card.layout(x, y, x + card.measuredWidth, y + card.measuredHeight)
        }
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility != VISIBLE && isAttachedToWindow) post { if (active?.surface === this) dismiss() }
        }
    }
}
