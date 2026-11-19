package com.axoo.features.posts.presentation.components

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Animatable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import coil.dispose
import coil.load
import coil.size.Precision

/** Shared by the picker, post cards and comments. */
internal class MediaImageView(context: Context) : FrameLayout(context) {
    private val preview = ImageView(context).apply {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = 10 * resources.displayMetrics.density
            setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP))
        }
    }
    private val image = ImageView(context)
    private val spinner = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        visibility = GONE
    }
    private var source: String? = null
    private var previewSource: String? = null
    private var pixelSize = 720
    private var requestID = 0
    var ready = false
        private set
    val drawable get() = image.drawable
    var onReady: (() -> Unit)? = null
    var animationsRunning = true
        set(value) { field = value; updatePlayback() }
    var scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER
        set(value) { field = value; image.scaleType = value; preview.scaleType = value }
    var cornerRadius = 0f
        set(value) { field = value; clipToOutline = value > 0; invalidateOutline() }

    init {
        scaleType = ImageView.ScaleType.FIT_CENTER
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, width, height, cornerRadius)
            }
        }
        addView(preview, LayoutParams(-1, -1))
        addView(image, LayoutParams(-1, -1))
        val size = (24 * resources.displayMetrics.density).toInt()
        addView(spinner, LayoutParams(size, size, Gravity.CENTER))
    }

    fun bind(url: String?, previewURL: String? = null, maximumPixelSize: Int = 720) {
        if (source == url && previewSource == previewURL && pixelSize == maximumPixelSize) return
        clear()
        source = url
        previewSource = previewURL?.takeIf { it.isNotBlank() && it != url }
        pixelSize = maximumPixelSize
        visibility = if (url == null) GONE else VISIBLE
        loadImages()
    }

    private fun loadImages() {
        val url = source ?: return
        val expected = ++requestID
        spinner.visibility = VISIBLE
        previewSource?.let { value ->
            preview.load(value) {
                size(32); precision(Precision.INEXACT); allowHardware(false); crossfade(false)
                listener(onSuccess = { _, result -> (result.drawable as? Animatable)?.stop() })
            }
        }
        image.load(url) {
            size(pixelSize); precision(Precision.INEXACT); allowHardware(false); crossfade(false)
            listener(onSuccess = { _, _ ->
                if (requestID == expected) {
                    ready = true
                    preview.dispose(); preview.setImageDrawable(null)
                    spinner.visibility = GONE
                    updatePlayback()
                    onReady?.invoke()
                }
            }, onError = { _, _ -> if (requestID == expected) spinner.visibility = GONE })
        }
    }

    fun clear() {
        requestID++
        image.dispose(); preview.dispose()
        (image.drawable as? Animatable)?.stop()
        image.setImageDrawable(null); preview.setImageDrawable(null)
        spinner.visibility = GONE
        source = null; previewSource = null; ready = false
    }

    private fun updatePlayback() {
        (drawable as? Animatable)?.let { if (animationsRunning && isAttachedToWindow) it.start() else it.stop() }
    }
    override fun onDetachedFromWindow() {
        requestID++
        image.dispose(); preview.dispose()
        spinner.visibility = GONE
        (drawable as? Animatable)?.stop()
        super.onDetachedFromWindow()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ready) loadImages() else updatePlayback()
    }
}
