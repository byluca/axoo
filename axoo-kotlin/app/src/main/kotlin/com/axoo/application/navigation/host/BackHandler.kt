/**
 * Interfaccia per la gestione del tasto Indietro (Back).
 * Permette alle singole viste di intercettare l'azione prima del sistema di navigazione.
 */
package com.axoo.application.navigation.host

interface BackHandler {
    /**
     * Chiamato quando l'utente preme il tasto Indietro.
     * @return true se hai bloccato/gestito l'azione (es. hai chiuso la tastiera o mostrato un popup).
     * @return false se autorizzi il sistema a chiudere questa schermata e tornare a quella precedente.
     */
    fun onBackPressed(): Boolean
}
