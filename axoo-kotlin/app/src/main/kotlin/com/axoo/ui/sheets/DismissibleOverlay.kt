/**
 * Interfaccia per gli elementi in sovrimpressione (Bottom Sheet, dialoghi).
 * Definisce i metodi base per avviare la chiusura dell'elemento.
 */
package com.axoo.ui.sheets

interface DismissibleOverlay {
    /** Avvia l'animazione di entrata per mostrare il componente. */
    fun show()
    
    /** Avvia l'animazione di uscita per chiudere il componente. */
    fun dismiss()
    /** Avvisa il sistema quando l'animazione di uscita è terminata per rimuovere la vista. */
    fun addOnDismissedListener(listener: () -> Unit)
}
