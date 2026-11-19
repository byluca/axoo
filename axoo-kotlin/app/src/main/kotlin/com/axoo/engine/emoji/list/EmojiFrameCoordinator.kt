/*
 * EmojiFrameCoordinator
 *
 * Il vigile  delle emoji animate.
 * Accorpa le richieste di aggiornamento grafico per evitare i cali di FPS.
 * Se 50 emoji chiedono di essere ridisegnate nello stesso istante, invece di
 * forzare 50 aggiornamenti separati, le mettiamo in "sala d'attesa" (dentro un Hashset) e ridisegniamo
 * lo schermo UNA SOLA VOLTA al successivo frame (tramite Choreographer).
 *
 *
 * L'HashSet garantisce l'unicità. Se una stessa emoji "impazzisce" e chiede
 * l'aggiornamento 10 volte di fila prima che passi il bus, l'HashSet la aggiunge
 * alla lista d'attesa una volta sola, evitando duplicati e spreco di memoria.
 */
package com.axoo.engine.emoji.list

import android.view.Choreographer
import android.view.View

class EmojiFrameCoordinator {

    private val pending = HashSet<View>()
    private val flushSnapshot = ArrayList<View>()
    private var posted = false
    private var attached = true

    private val frameCallback = Choreographer.FrameCallback {
        posted = false
        if (!attached || pending.isEmpty()) return@FrameCallback
        flushSnapshot.clear()
        flushSnapshot.addAll(pending)
        pending.clear()

        for (index in flushSnapshot.indices) {
            val view = flushSnapshot[index]
            if (view.isAttachedToWindow) view.invalidate()
        }
        flushSnapshot.clear()
    }

    fun requestInvalidate(view: View) {
        if (!attached) return
        val added = pending.add(view)
        if (!added) return
        if (posted) return
        posted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun attach() {
        attached = true
    }

    fun cancel(view: View) {
        pending.remove(view)
    }

    fun shutdown() {
        attached = false
        if (posted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            posted = false
        }
        pending.clear()
        flushSnapshot.clear()
    }
}
