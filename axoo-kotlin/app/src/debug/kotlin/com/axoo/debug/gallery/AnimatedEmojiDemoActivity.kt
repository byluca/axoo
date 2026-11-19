package com.axoo.debug.gallery

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.lottie.LottieHost
import kotlin.math.roundToInt

/**
 * Debug-only laboratory for the animated emoji runtime.
 *
 * Mostra il primo playback automatico del runtime.
 */
class AnimatedEmojiDemoActivity : Activity() {

    private lateinit var firstHostView: DemoEmojiHostView
    private lateinit var secondHostView: DemoEmojiHostView
    private lateinit var firstLease: AnimatedEmojiLease
    private lateinit var secondLease: AnimatedEmojiLease
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Animated emoji runtime"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        root.addView(TextView(this).apply {
            text = "Una emoji, due host"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "2 acquire → 2 lease → 1 drawable → 1 player"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }, matchWrap())

        val hostsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        firstHostView = DemoEmojiHostView(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        secondHostView = DemoEmojiHostView(this).apply {
            setBackgroundColor(Color.rgb(232, 240, 255))
        }
        hostsRow.addView(firstHostView, squareHostParams())
        hostsRow.addView(secondHostView, squareHostParams())
        root.addView(hostsRow, matchWrap())

        status = TextView(this).apply {
            text = "Caricamento…"
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(status, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(Button(this).apply {
            text = "Play"
            setOnClickListener {
                firstLease.play()
                secondLease.play()
                status.text = "Play"
            }
        }, wrapWrap())
        controls.addView(Button(this).apply {
            text = "Pause"
            setOnClickListener {
                firstLease.pause()
                secondLease.pause()
                status.text = "Pausa"
            }
        }, wrapWrap())
        root.addView(controls, matchWrap())

        root.addView(TextView(this).apply {
            text = "Entrambe le View disegnano la stessa bitmap prodotta dallo stesso player."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }, matchWrap())

        setContentView(root)

        val key = AnimatedEmojiKey(
            id = "fire",
            path = DEMO_ASSET,
            sizePx = dp(132),
        )
        firstLease = AnimatedEmojiRegistry.acquire(this, key, firstHostView)
        secondLease = AnimatedEmojiRegistry.acquire(this, key, secondHostView)
        check(firstLease.drawable === secondLease.drawable)
        firstHostView.setLease(firstLease)
        secondHostView.setLease(secondLease)
        firstLease.play()
        secondLease.play()
        status.text = "Attiva: 2 lease, 1 drawable condiviso"
    }

    override fun onPause() {
        if (::firstLease.isInitialized) {
            firstLease.pause()
            secondLease.pause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::firstLease.isInitialized) {
            firstLease.play()
            secondLease.play()
        }
    }

    override fun onDestroy() {
        if (::firstLease.isInitialized) {
            firstHostView.clearEmoji()
            secondHostView.clearEmoji()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun wrapWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun squareHostParams() = LinearLayout.LayoutParams(dp(132), dp(132)).apply {
        marginStart = dp(4)
        marginEnd = dp(4)
    }

    private companion object {
        const val DEMO_ASSET = "emoji/animated_tgs/pack-1/pack-1-fire-emoji.tgs"
    }

    /**
     * Le View demo conoscono soltanto la propria [AnimatedEmojiLease].
     * Non conoscono registry internals, ImageReceiver, LottiePlayer, bitmap o JNI.
     */
    private class DemoEmojiHostView(context: Context) : View(context), LottieHost {
        private val emojiBounds = Rect()
        private var lease: AnimatedEmojiLease? = null
        private var lottieAttached = false

        override val isAttachedForLottie: Boolean
            get() = lottieAttached

        override fun invalidateLottie(bounds: Rect?) {
            invalidate()
        }

        fun setLease(next: AnimatedEmojiLease) {
            if (lease === next) return
            clearEmoji()
            lease = next
            next.drawable.refreshHostAttachment()
        }

        fun clearEmoji() {
            lease?.close()
            lease = null
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            lottieAttached = true
            lease?.drawable?.refreshHostAttachment()
        }

        override fun onDetachedFromWindow() {
            lottieAttached = false
            lease?.drawable?.refreshHostAttachment()
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            emojiBounds.set(0, 0, width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            lease?.drawable?.draw(canvas, emojiBounds)
        }
    }
}
