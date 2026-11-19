/**
 * Contenitore radice dell'intera applicazione. Divide lo schermo tra la navigazione principale (NavigationHost)
 * e gli elementi in sovrimpressione (OverlayHost), e intercetta il tasto Indietro.
 */
package com.axoo.application.navigation.host

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import com.axoo.application.navigation.factory.ScreenFactory
import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Tab

@SuppressLint("ViewConstructor")
class AppRootView(
    context: Context,
    initialNavigationSnapshot: NavigationSnapshot? = null,
    initialTab: Tab = Tab.Home,
    screenFactory: ScreenFactory,
) : FrameLayout(context) {
    val navigationHost = NavigationHost(
        context,
        screenFactory = screenFactory,
        initialSnapshot = initialNavigationSnapshot,
        initialTab = initialTab,
    )
    val overlayHost = OverlayHost(context)

    //Il blocco init { ... } viene eseguito una sola volta quando l'app viene avviata. Serve a piazzare fisicamente le scatole
    //sullo schermo.
    init {
        addView(
            navigationHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            overlayHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun onBackPressed(): Boolean {
        if (overlayHost.dismissTopSheet()) return true
        return navigationHost.onBackPressed()
    }

    fun saveNavigationState(): String = navigationHost.saveState().encode()
}
