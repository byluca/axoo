/**
 * Interfaccia base per tutte le schermate dell'app.
 * Espone i metodi per gestire il ciclo di vita visivo (montaggio, visibilità, distruzione).
 */
package com.axoo.application.navigation.route

// Tutte le schermate implementano questo contratto per ricevere il proprio lifecycle.
interface NativeScreen {
    val route: Route

    fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) = Unit

    /** L'utente sta trascinando la schermata per tornare indietro. Utile per mettere in pausa video/animazioni pesanti. */
    fun onInteractiveTransitionStart(isOpening: Boolean) {
        onScreenTransitionStart(isOpening = isOpening, isBackward = true)
    }

    /** La transizione è finita, la schermata è stabile in primo piano. Qui puoi avviare lavori pesanti (es. far partire un video o caricare dati). */
    fun onScreenFullyVisible() = Unit

    fun onScreenHidden() = Unit
    fun onScreenDestroyed() = Unit
}
