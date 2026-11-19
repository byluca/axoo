package com.axoo.features.events.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.axoo.features.events.domain.CityEvent
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class EventCardView(context: Context, detail: Boolean = false) : NativePillCard(context) {
    private val cover = object : ImageView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(width * 4 / 3, MeasureSpec.EXACTLY),
            )
        }
    }.apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(NativeColors.surface)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val title = eventText(context, 21f, heading = true).apply {
        if (!detail) { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    }
    private val date = eventText(context, 14f).apply { setTextColor(NativeColors.accent) }
    private val venue = eventText(context, 14f)
    private val entry = eventText(context, 13f).apply { setTextColor(NativeColors.textSecondary) }

    init {
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(title)
            addView(date, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(venue, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(entry)
        })
    }

    fun bind(event: CityEvent) {
        cover.load(event.coverUrl) {
            placeholder(ColorDrawable(NativeColors.surface))
            error(ColorDrawable(NativeColors.surface))
        }
        title.text = event.title
        date.text = if (event.cancelled) "Evento annullato" else eventDate(event.startsAt)
        venue.text = event.venue
        entry.text = event.entryInfo
    }
}

internal fun eventText(context: Context, size: Float, heading: Boolean = false) = TextView(context).apply {
    textSize = size
    typeface = if (heading) context.baloo2Typeface(700) else context.interTypeface(400)
    setTextColor(NativeColors.textPrimary)
    includeFontPadding = false
}

internal fun eventDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Europe/Rome"))
        .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ITALIAN))
        .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
}.getOrDefault("Data da confermare")
