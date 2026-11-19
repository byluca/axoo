// Lista specializzata per le emoji animate. Gestisce play/pausa e il coordinatore FPS.
package com.axoo.engine.emoji.list

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.axoo.engine.emoji.picker.EmojiPickerPlaybackCoordinator

enum class EmojiScrollState {
    DETACHED, // lista staccata dallo schermo (app è in background o schermata chiusa)
    IDLE, // a riposo, lista ferma
    DRAGGING, // dito sullo schermo, scrolling classico
    SETTLING, // il dito è staccato, swipe veloce e scendiamo per inerzia
    PROGRAMMATIC_SCROLL, // scroll automatico da codice (es. tasto "Torna in cima")
}

// Chi sta SOPRA la lista (es. la schermata) implementa questa
// interfaccia per essere avvisato quando l'utente scorre la lista.
interface EmojiPlaybackController {
    fun onEmojiViewportChanged(recyclerView: EmojiRecyclerView)
}

// Le singole View DENTRO la lista (es. i Post) implementano questa
// interfaccia per ubbidire agli ordini della lista (accendere/spegnere le emoji).
interface EmojiInlineConsumer {
    fun setEmojiAnimating(value: Boolean)

    // Collega la View al "vigile urbano" (Coordinator) per non laggare
    fun setEmojiFrameCoordinator(coordinator: EmojiFrameCoordinator?) = Unit

    // Ritorna true se almeno un'emoji sta attualmente animando in questa View
    fun isEmojiPlaying(): Boolean = false
}

class EmojiRecyclerView(context: Context) : BaseRecyclerView(context) {

    private var playbackController: EmojiPlaybackController? = null
    val frameCoordinator: EmojiFrameCoordinator = EmojiFrameCoordinator()
    private var pickerCoordinator: EmojiPickerPlaybackCoordinator? = null
    internal val surfaceCoordinator: EmojiPickerPlaybackCoordinator
        get() = checkNotNull(pickerCoordinator) { "Picker playback is not enabled" }
    internal val surfaceCoordinatorOrNull: EmojiPickerPlaybackCoordinator?
        get() = pickerCoordinator
    private var currentEmojiState: EmojiScrollState = EmojiScrollState.DETACHED
    // Di default le emoji sono attive quando la lista è visibile
    private var gate: EmojiPlaybackGate = EmojiPlaybackGate.FullyVisible
    private var lastDispatchedAnimating = false
    private var programmaticScroll = false

    val emojiScrollState: EmojiScrollState
        get() = currentEmojiState

    init {
        addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    handleRecyclerScrollState(newState)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    playbackController?.onEmojiViewportChanged(this@EmojiRecyclerView)
                    pickerCoordinator?.markDirty()
                }
            },
        )
    }

    fun setEmojiPlaybackController(controller: EmojiPlaybackController?) {
        playbackController = controller
        controller?.onEmojiViewportChanged(this)
    }

    // Aggiorna lo stato (acceso/spento) e lo applica subito a tutte le emoji.
    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        if (this.gate == gate) return
        this.gate = gate
        reconcilePlayback()
        pickerCoordinator?.markDirty()
        playbackController?.onEmojiViewportChanged(this)
    }

    val currentPlaybackGate: EmojiPlaybackGate
        get() = gate

    internal fun enablePickerPlayback() {
        if (pickerCoordinator != null) return
        pickerCoordinator = EmojiPickerPlaybackCoordinator(this).also { coordinator ->
            if (isAttachedToWindow) coordinator.attach()
        }
    }

    val playbackAllowed: Boolean
        get() = computeEmojiPlaybackAllowed(
            gate = gate,
            isAttachedToWindow = isAttachedToWindow,
        )

    fun beginProgrammaticScroll() {
        programmaticScroll = true
        transitionTo(EmojiScrollState.PROGRAMMATIC_SCROLL)
        playbackController?.onEmojiViewportChanged(this)
    }

    fun finishProgrammaticScrollIfIdle() {
        if (scrollState == SCROLL_STATE_IDLE) {
            programmaticScroll = false
            transitionTo(EmojiScrollState.IDLE)
            playbackController?.onEmojiViewportChanged(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        frameCoordinator.attach()
        pickerCoordinator?.attach()
        // Ripristina lo stato ma NON forzarlo ad acceso (lo decide il gate).
        transitionTo(EmojiScrollState.IDLE)
        playbackController?.onEmojiViewportChanged(this)
    }

    override fun onDetachedFromWindow() {
        programmaticScroll = false
        transitionTo(EmojiScrollState.DETACHED)
        playbackController?.onEmojiViewportChanged(this)
        pickerCoordinator?.detach()
        frameCoordinator.shutdown()
        super.onDetachedFromWindow()
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        if (child is EmojiInlineConsumer) {
            child.setEmojiFrameCoordinator(frameCoordinator)
            // La nuova View deve rispettare lo stato attuale (non farla partire se lo schermo è in pausa).
            child.setEmojiAnimating(playbackAllowed)
        }
        pickerCoordinator?.markDirty()
        playbackController?.onEmojiViewportChanged(this)
    }

    override fun onChildDetachedFromWindow(child: View) {
        super.onChildDetachedFromWindow(child)
        if (child is EmojiInlineConsumer) {
            child.setEmojiFrameCoordinator(null)
        }
        pickerCoordinator?.markDirty()
        playbackController?.onEmojiViewportChanged(this)
    }

    private fun handleRecyclerScrollState(newState: Int) {
        when (newState) {
            SCROLL_STATE_IDLE -> {
                if (programmaticScroll) {
                    finishProgrammaticScrollIfIdle()
                } else {
                    transitionTo(EmojiScrollState.IDLE)
                }
            }
            SCROLL_STATE_DRAGGING -> {
                if (!programmaticScroll) {
                    transitionTo(EmojiScrollState.DRAGGING)
                }
            }
            SCROLL_STATE_SETTLING -> {
                transitionTo(EmojiScrollState.SETTLING)
            }
        }
        playbackController?.onEmojiViewportChanged(this)
    }

    private fun transitionTo(state: EmojiScrollState) {
        if (currentEmojiState == state) return
        currentEmojiState = state
        pickerCoordinator?.onScrollStateChanged()
        reconcilePlayback()
    }

    private fun reconcilePlayback() {
        val allowed = playbackAllowed
        if (allowed == lastDispatchedAnimating && childCount == 0) return
        lastDispatchedAnimating = allowed
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is EmojiInlineConsumer) {
                child.setEmojiAnimating(allowed)
            }
        }
    }
}
