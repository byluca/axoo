package com.axoo.features.events.presentation

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.events.domain.CityEvent
import com.axoo.features.events.presentation.components.EventCardView
import com.axoo.features.events.presentation.components.eventDate
import com.axoo.features.events.presentation.components.eventText
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class EventDetailHost(
    context: Context,
    private val eventId: String,
    private val viewModel: EventsViewModel,
    private val owner: LifecycleOwner,
    onBack: () -> Unit,
) : LinearLayout(context), NativeScreen {
    override val route: Route = Route.EventDetail(eventId)
    private val card = EventCardView(context, detail = true)
    private val description = eventText(context, 16f).apply { setTextIsSelectable(true); autoLinkMask = android.text.util.Linkify.WEB_URLS or android.text.util.Linkify.PHONE_NUMBERS; linksClickable = true }
    private val end = eventText(context, 14f).apply { setTextColor(NativeColors.textSecondary) }
    private val maps = eventText(context, 15f).apply {
        setTextColor(NativeColors.accent)
        setPadding(0, dp(12), 0, dp(12))
        minimumHeight = dp(48)
    }
    private val status = eventText(context, 15f).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setTextColor(NativeColors.textSecondary)
        setOnClickListener { viewModel.open(eventId) }
    }
    private val body = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(24))
        addView(card)
        addView(end, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        addView(maps)
        addView(description, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }
    private var rendered: CityEvent? = null
    private var observer: Job? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(NativeColors.surface)
        val bar = FrameLayout(context).apply {
            setBackgroundColor(NativeColors.card)
            addView(eventText(context, 22f, heading = true).apply { text = "Evento"; gravity = Gravity.CENTER_VERTICAL },
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { leftMargin = dp(56) })
            addView(ArrowBackButton(context).apply {
                contentDescription = "Indietro"
                setOnClickListener { onBack() }
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START))
        }
        addView(bar)
        addView(ScrollView(context).apply {
            clipToPadding = false
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(status)
                addView(body)
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        body.visibility = GONE
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bar.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, 0, 0)
            body.setPadding(dp(16), dp(12), dp(16), dp(24) + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
    }

    override fun onScreenFullyVisible() {
        ViewCompat.requestApplyInsets(this)
        if (observer != null) return
        viewModel.open(eventId)
        observer = owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val event = state.detail?.takeIf { it.id == eventId }
                    if (event != null && rendered != event) {
                        rendered = event
                        card.bind(event)
                        description.text = event.description
                        end.text = "Fine: ${eventDate(event.endsAt)}"
                        maps.text = "${event.address}\nApri nelle mappe ↗"
                        maps.setOnClickListener {
                            openLink(Uri.parse("https://www.google.com/maps/search/").buildUpon()
                                .appendQueryParameter("api", "1").appendQueryParameter("query", "${event.venue}, ${event.address}").build())
                        }
                    }
                    body.visibility = if (event != null) VISIBLE else GONE
                    status.text = when {
                        state.detailError -> "Evento non disponibile. Tocca per riprovare."
                        event == null -> "Caricamento evento…"
                        else -> ""
                    }
                    status.visibility = if (status.text.isEmpty()) GONE else VISIBLE
                    status.isClickable = state.detailError

                }
            }
        }
    }

    private fun openLink(uri: Uri) {
        if (uri.scheme != "https" && uri.scheme != "http") return
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Nessuna app disponibile per aprire il link", Toast.LENGTH_SHORT).show() }
    }

    override fun onScreenHidden() { observer?.cancel(); observer = null }
    override fun onScreenDestroyed() = onScreenHidden()
}
