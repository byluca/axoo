package com.axoo.ui.text

import com.axoo.ui.theme.NativeColors
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Path
import android.graphics.Region
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.net.toUri
import androidx.core.text.util.LinkifyCompat
import kotlin.math.abs

internal fun withWebLinks(text: CharSequence): CharSequence {
    val result = SpannableStringBuilder(text)
    LinkifyCompat.addLinks(result, Linkify.WEB_URLS)
    result.getSpans(0, result.length, URLSpan::class.java).forEach { span ->
        val start = result.getSpanStart(span)
        val end = result.getSpanEnd(span)
        result.removeSpan(span)
        val uri = span.url.toUri()
        if (uri.scheme == "http" || uri.scheme == "https") {
            result.setSpan(object : URLSpan(span.url) {
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = NativeColors.link
                    ds.isUnderlineText = false
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return result
}

/** Hit testing uses the displayed layout, including wrapped links and emoji attachments. */
internal class WebLinkTouch(private val view: View) {
    private var downX = 0f
    private var downY = 0f
    private var downLink: String? = null
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop

    fun onTouch(event: MotionEvent, layout: StaticLayout?, left: Float, top: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downLink = linkAt(layout, event.x - left, event.y - top)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) downLink = null
            }
            MotionEvent.ACTION_CANCEL -> downLink = null
            MotionEvent.ACTION_UP -> {
                val url = downLink
                downLink = null
                if (url != null && url == linkAt(layout, event.x - left, event.y - top)) {
                    try {
                        view.context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        // Devices without a browser still consume the link tap.
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun linkAt(layout: StaticLayout?, x: Float, y: Float): String? {
        layout ?: return null
        if (x < 0 || y < 0 || x >= layout.width || y >= layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val text = layout.text as? Spanned ?: return null
        val path = Path()
        val region = Region()
        val clip = Region(0, 0, layout.width, layout.height)
        return text.getSpans(0, text.length, URLSpan::class.java).firstOrNull { span ->
            path.reset()
            layout.getSelectionPath(text.getSpanStart(span), text.getSpanEnd(span), path)
            region.setPath(path, clip)
            region.contains(x.toInt(), y.toInt())
        }?.url
    }
}
