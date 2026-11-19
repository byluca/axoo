package com.axoo.features.events.presentation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import coil.load
import com.axoo.R
import com.axoo.features.events.domain.CityEvent
import com.axoo.features.events.domain.EventInput
import com.axoo.features.events.presentation.components.eventDate
import com.axoo.features.events.presentation.components.eventText
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.AxooTextInput
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Full-screen form; its controls and surfaces use the app's native components. */
internal class EventEditorDialog(
    private val context: Context,
    owner: LifecycleOwner,
    private val model: EventsViewModel,
    private val event: CityEvent? = null,
) {
    private val zone = ZoneId.of("Europe/Rome")
    private var start = event?.startsAt?.let { runCatching { Instant.parse(it).atZone(zone) }.getOrNull() }
        ?: ZonedDateTime.now(zone).plusDays(1).withHour(21).withMinute(0).withSecond(0).withNano(0)
    private var end = event?.endsAt?.let { runCatching { Instant.parse(it).atZone(zone) }.getOrNull() } ?: start.plusHours(4)
    private var photo: Uri? = null
    private var busy = false
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(NativeColors.surface)
        tag = "event-editor"
    }
    private val header = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(NativeColors.card)
    }
    private val form = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = minOf(MeasureSpec.getSize(widthMeasureSpec), context.dp(560))
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }.apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(24)) }
    private val footer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(NativeColors.card)
        tag = "event-editor-footer"
    }
    private val back = ArrowBackButton(context).apply { contentDescription = "Indietro" }
    private val save = NativeLoadingButton(context)
    private val cover = NativeLoadingButton(context, primary = false, textSizeSp = 14f)
    private val coverImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(NativeColors.surface)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        tag = "event-cover-preview"
    }
    private val coverEmpty = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
    }
    private val startButton = dateControl("event-start")
    private val endButton = dateControl("event-end")
    private val error = eventText(context, 14f).apply {
        setTextColor(NativeColors.destructive); visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val title: AxooTextInput
    private val description: AxooTextInput
    private val venue: AxooTextInput
    private val address: AxooTextInput
    private val entry: AxooTextInput
    private val picker = (owner as ComponentActivity).activityResultRegistry.register(
        "event-cover-${UUID.randomUUID()}", ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) { photo = uri; updateCover() } }
    private val dialog = ComponentDialog(context, R.style.Theme_Axoo)

    init {
        header.addView(back, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))
        header.addView(eventText(context, 22f, heading = true).apply {
            text = if (event == null) "Crea evento" else "Modifica evento"
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        back.setOnClickListener { close() }
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(56)))
        val scroll = ScrollView(context).apply {
            isFillViewport = false; clipToPadding = false
            addView(FrameLayout(context).apply {
                addView(form, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            })
            tag = "event-editor-scroll"
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        form.addView(eventText(context, 13f).apply {
            text = "L’evento sarà visibile nella città della tua università."
            setTextColor(NativeColors.textSecondary)
        }, formLayout())
        val poster = section()
        poster.setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(4))
        val coverFrame = FrameLayout(context).apply {
            applyNativePillSurface(12f, NativeColors.surface, borderColor = null); clipToOutline = true
            isClickable = true; isFocusable = true; contentDescription = "Scegli locandina"
            setOnClickListener { chooseCover() }
            addView(coverImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(coverEmpty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        coverEmpty.addView(ImageView(context).apply {
            imageTintList = ColorStateList.valueOf(NativeColors.textSecondary)
            load("file:///android_asset/icons/photo.svg")
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        coverEmpty.addView(eventText(context, 18f, heading = true).apply { text = "Aggiungi la locandina" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
        coverEmpty.addView(eventText(context, 13f).apply { text = "Tocca per scegliere una foto"; setTextColor(NativeColors.textSecondary) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) })
        poster.addView(coverFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(200)))
        poster.addView(cover, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(44)))
        cover.setOnClickListener { chooseCover() }
        title = field(section(), "Titolo", event?.title.orEmpty(), 120, "event-title", "Es. Aperitivo universitario")
        val whenWhere = section("Quando e dove")
        val dates = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for ((index, item) in listOf("Inizio" to startButton, "Fine" to endButton).withIndex()) {
            dates.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(item.first))
                addView(item.second, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (index == 1) leftMargin = context.dp(12) })
        }
        whenWhere.addView(dates, groupLayout(16))
        whenWhere.addView(eventText(context, 12f).apply { text = "Orari di Roma"; setTextColor(NativeColors.textSecondary) }, groupLayout(8))
        startButton.setOnClickListener { pickDate(start) { start = it; dates() } }
        endButton.setOnClickListener { pickDate(end) { end = it; dates() } }
        venue = field(whenWhere, "Locale / luogo", event?.venue.orEmpty(), 160, "event-venue", "Nome del locale o del luogo")
        address = field(whenWhere, "Indirizzo completo", event?.address.orEmpty(), 300, "event-address", "Via e numero civico")
        val details = section("Dettagli")
        description = field(details, "Descrizione e contatti", event?.description.orEmpty(), 10000, "event-description", "Racconta l’evento e come partecipare", multiline = true)
        entry = field(details, "Ingresso e prezzo", event?.entryInfo.orEmpty(), 160, "event-entry", "Es. Ingresso libero")
        footer.addView(error, groupLayout(0).apply { bottomMargin = context.dp(8) })
        footer.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(54)))
        root.addView(View(context).apply { setBackgroundColor(NativeColors.cardBorder) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1)))
        root.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        save.tag = "event-save"; save.contentDescription = if (event == null) "Pubblica evento" else "Salva modifiche"
        save.setOnClickListener { publish() }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { close() }
        })
        dialog.setOnDismissListener { picker.unregister() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = maxOf(system.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            header.setPadding(context.dp(12) + system.left, system.top, context.dp(12) + system.right, 0)
            header.layoutParams.height = context.dp(56) + system.top
            footer.setPadding(context.dp(20) + system.left, context.dp(12), context.dp(20) + system.right, context.dp(12) + bottom)
            scroll.setPadding(system.left, 0, system.right, 0)
            insets
        }
        dates(); updateCover(); setBusy(false)
    }

    fun show() {
        dialog.show()
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(NativeColors.surface))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun section(heading: String? = null) = NativePillCard(context).apply {
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        heading?.let { addView(eventText(context, 18f, heading = true).apply { text = it; ViewCompat.setAccessibilityHeading(this, true) }) }
        form.addView(this, formLayout())
    }
    private fun formLayout() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(16) }
    private fun groupLayout(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }
    private fun label(value: String) = eventText(context, 13f).apply { text = value; setTextColor(NativeColors.textSecondary) }
    private fun field(parent: LinearLayout, name: String, value: String, limit: Int, testTag: String, placeholder: String, multiline: Boolean = false): AxooTextInput {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val caption = label(name)
        container.addView(caption)
        val input = AxooTextInput(context).apply {
            id = View.generateViewId(); tag = testTag; hint = placeholder
            setText(value); filters = arrayOf(InputFilter.LengthFilter(limit))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            setSingleLine(!multiline); minimumHeight = context.dp(52)
            if (multiline) { minLines = 4; maxLines = 10; gravity = Gravity.TOP }
        }
        caption.labelFor = input.id
        container.addView(input, groupLayout(6))
        parent.addView(container, groupLayout(if (parent.childCount == 0) 0 else 16))
        return input
    }
    private fun dateControl(testTag: String) = eventText(context, 14f).apply {
        tag = testTag; isClickable = true; isFocusable = true
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        applyNativePillSurface(16f, NativeColors.surface, borderColor = null)
    }
    private fun chooseCover() { if (!busy) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    private fun updateCover() {
        val source = photo?.toString() ?: event?.coverUrl.orEmpty()
        coverEmpty.visibility = if (source.isBlank()) View.VISIBLE else View.GONE
        coverImage.visibility = if (source.isBlank()) View.GONE else View.VISIBLE
        coverImage.load(source)
        cover.bind(if (source.isBlank()) "Scegli locandina" else "Cambia immagine", !busy, false)
    }
    private fun publish() {
        if (busy) return
        val input = input() ?: return
        setBusy(true); showError("")
        model.save(event?.id, input, photo) { ok ->
            setBusy(false)
            if (ok) dialog.dismiss() else showError("Salvataggio non riuscito. Controlla la connessione e il permesso admin, poi riprova.")
        }
    }
    private fun showError(message: String) { error.text = message; error.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE }
    private fun input(): EventInput? {
        val fields = listOf(title, description, venue, address, entry)
        fields.forEach { it.error = if (it.text.toString().isBlank()) "Campo obbligatorio" else null }
        val message = when {
            fields.any { it.text.toString().isBlank() } -> "Compila tutti i campi."
            photo == null && event?.coverUrl.isNullOrBlank() -> "Scegli una locandina."
            !end.isAfter(start) -> "La fine deve essere successiva all’inizio."
            !end.isAfter(ZonedDateTime.now(zone)) -> "L’evento è già concluso."
            else -> ""
        }
        showError(message)
        if (message.isNotEmpty()) return null
        return EventInput(title.text.toString().trim(), description.text.toString().trim(), event?.coverUrl.orEmpty(),
            start.toInstant().toString(), end.toInstant().toString(), venue.text.toString().trim(), address.text.toString().trim(), entry.text.toString().trim())
    }
    private fun setBusy(value: Boolean) {
        busy = value
        listOf(title, description, venue, address, entry, startButton, endButton, back).forEach { it.isEnabled = !value }
        save.bind(if (event == null) "Pubblica" else "Salva modifiche", !value, value)
        updateCover()
        dialog.setCancelable(!value)
    }
    private fun close() {
        if (busy) return
        AlertDialog.Builder(context, R.style.Theme_Axoo_OverlayCard).setTitle("Uscire dalla modifica?").setMessage("Le modifiche non salvate andranno perse.")
            .setNegativeButton("Resta", null).setPositiveButton("Esci") { _, _ -> dialog.dismiss() }.show()
    }
    private fun dates() {
        startButton.text = eventDate(start.toInstant().toString()).replace(" · ", "\n")
        endButton.text = eventDate(end.toInstant().toString()).replace(" · ", "\n")
        startButton.contentDescription = "Inizio: " + eventDate(start.toInstant().toString())
        endButton.contentDescription = "Fine: " + eventDate(end.toInstant().toString())
    }
    private fun pickDate(value: ZonedDateTime, done: (ZonedDateTime) -> Unit) {
        if (busy) return
        android.app.DatePickerDialog(context, { _, year, month, day ->
            android.app.TimePickerDialog(context, { _, hour, minute ->
                done(ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone))
            }, value.hour, value.minute, true).show()
        }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    }
}
