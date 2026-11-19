package com.axoo.features.events.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import coil.load
import androidx.appcompat.view.ContextThemeWrapper
import com.axoo.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.axoo.features.events.presentation.components.EventCardView
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.application.navigation.components.NativeBottomNavigationOverlay
import com.axoo.application.navigation.route.BottomTab
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.events.data.CampusEvent
import com.axoo.features.events.data.toCityEvent
import com.axoo.features.notifications.presentation.NotificationsViewModel
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.components.NativeSkeletonCardView
import com.axoo.ui.components.SkeletonType
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface
import com.axoo.ui.theme.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class EventsHost(context: Context, private val model: EventsViewModel,
    private val notifications: NotificationsViewModel, private val owner: LifecycleOwner,
    onTab: (BottomTab) -> Unit, onOpen: (CampusEvent) -> Unit,
) : LinearLayout(context), NativeScreen {
    override val route: Route = Route.Events
    private val bottom = NativeBottomNavigationBar(context)
    private val adapter = EventsAdapter(onOpen = onOpen, onMenu = { item ->
        OptionsSheet.present(this, title = item.title, actions = listOf(
            OptionsSheetAction("Modifica evento", iconAsset = "pencil.svg", onClick = {
                EventEditorDialog(context, owner, model, item.toCityEvent(true)).show()
            }), OptionsSheetAction(
            text = "Elimina evento", iconAsset = "delete.svg", destructive = true,
            onClick = {
                OverlayCard.present(this, "Eliminare l'evento?", item.title,
                    primaryAction = OverlayCardAction("Elimina", destructive = true) { model.delete(item.id) },
                    secondaryAction = OverlayCardAction("Annulla") {})
            },
        )))
    })
    private val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context); adapter = this@EventsHost.adapter
        clipToPadding = false; setPadding(0, dp(8), 0, dp(12))
        contentDescription = "Eventi della tua città"
    }
    private val refresh = SwipeRefreshLayout(context).apply {
        addView(list); setColorSchemeColors(NativeColors.accent); setOnRefreshListener { model.refresh() }
    }
    private val skeleton = NativeSkeletonCardView(context, rows = 3, type = SkeletonType.Notification)
    private val status = TextView(context).apply {
        textSize = 16f; gravity = Gravity.CENTER; typeface = context.baloo2Typeface(600)
        setTextColor(NativeColors.textSecondary); setPadding(dp(24), dp(24), dp(24), dp(24))
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val errorLabel = TextView(context).apply {
        textSize = 14f; gravity = Gravity.CENTER; typeface = context.interTypeface(400)
        setTextColor(NativeColors.textSecondary); setPadding(dp(20), dp(8), dp(20), dp(8))
    }
    private val more = NativeLoadingButton(context, primary = false)
    private val create = FloatingActionButton(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)).apply {
        contentDescription = "Crea evento"
        visibility = GONE
        size = FloatingActionButton.SIZE_NORMAL
        useCompatPadding = false
        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(dp(28f)).build()
        backgroundTintList = ColorStateList.valueOf(NativeColors.accent)
        imageTintList = ColorStateList.valueOf(NativeColors.onAccent)
        load("file:///android_asset/icons/plus.svg")
        setOnClickListener { EventEditorDialog(context, owner, model).show() }
    }
    private var observer: Job? = null
    private var rendered: List<CampusEvent>? = null
    private var renderedManage = false
    init {
        orientation = VERTICAL; setBackgroundColor(NativeColors.surface)
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(NativeColors.card); setPadding(dp(16), 0, dp(12), 0)
        }
        header.addView(TextView(context).apply {
            text = "Prossimi eventi"; textSize = 24f; typeface = context.baloo2Typeface(800); setTextColor(NativeColors.textPrimary)
            ViewCompat.setAccessibilityHeading(this, true)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(FrameLayout(context).apply {
                addView(refresh, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(skeleton, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(status, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(errorLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(more, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }
        val overlay = NativeBottomNavigationOverlay(context, body, bottom) { inset ->
            body.setPadding(0, 0, 0, inset)
            create.translationY = -inset.toFloat()
        }
        overlay.addView(create, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(20); bottomMargin = dp(16)
        })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        bottom.bind(BottomTab.EVENTS); bottom.setOnTabSelected(onTab)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            header.layoutParams.height = dp(52) + top; header.setPadding(dp(16), top, dp(12), 0)
            (create.layoutParams as FrameLayout.LayoutParams).rightMargin = dp(20) + insets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            bottom.setNavigationBottomInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom); insets
        }
    }
    override fun onScreenFullyVisible() {
        ViewCompat.requestApplyInsets(this); model.refresh(); observer?.cancel()
        observer = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect(::render) }; launch { notifications.unread.collect(bottom::bindUnreadCount) }
            }
        }
    }
    override fun onScreenHidden() { observer?.cancel(); observer = null }
    override fun onScreenDestroyed() { onScreenHidden() }
    private fun render(state: EventsState) {
        refresh.isRefreshing = state.loading && state.items.isNotEmpty()
        skeleton.visibility = if (state.loading && state.items.isEmpty()) VISIBLE else GONE
        create.visibility = if (state.canManage) VISIBLE else GONE; create.isEnabled = !state.saving
        list.setPadding(0, dp(8), 0, dp(if (state.canManage) 88 else 16))
        (more.layoutParams as LayoutParams).marginEnd = dp(if (state.canManage) 80 else 0)
        if (rendered != state.items || renderedManage != state.canManage || adapter.busy != state.saving) {
            rendered = state.items; renderedManage = state.canManage
            adapter.items = state.items; adapter.canManage = state.canManage; adapter.busy = state.saving; adapter.notifyDataSetChanged()
        }
        status.visibility = if (state.items.isEmpty() && !state.loading) VISIBLE else GONE
        status.text = state.saveError ?: if (state.error) "Impossibile caricare gli eventi" else "Nessun evento in programma"
        errorLabel.text = state.saveError ?: if (state.error) "Impossibile caricare gli eventi." else ""
        errorLabel.visibility = if (state.items.isNotEmpty() && (state.error || state.saveError != null)) VISIBLE else GONE
        more.visibility = if (state.cursor != null || state.error || state.saveError != null) VISIBLE else GONE
        more.bind(if (state.error || state.saveError != null) "Riprova" else "Altri eventi", !state.saving, state.loading)
        more.setOnClickListener { if (state.error || state.saveError != null) model.refresh() else model.loadMore() }
    }
}

private class EventsAdapter(private val onOpen: (CampusEvent) -> Unit, private val onMenu: (CampusEvent) -> Unit) : RecyclerView.Adapter<EventsAdapter.Holder>() {
    var items: List<CampusEvent> = emptyList(); var canManage = false; var busy = false
    class Holder(val card: EventCardView) : RecyclerView.ViewHolder(card)
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(EventCardView(parent.context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(parent.context.dp(16), parent.context.dp(8), parent.context.dp(16), parent.context.dp(8))
        }
    })
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val event = items[position]
        holder.card.bind(event.toCityEvent(canManage))
        holder.card.setOnClickListener { onOpen(event) }
        holder.card.setOnLongClickListener { if (canManage && !busy) { onMenu(event); true } else false }
    }
}
