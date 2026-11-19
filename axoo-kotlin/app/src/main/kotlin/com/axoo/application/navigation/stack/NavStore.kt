/**
 * Motore logico e archivio della navigazione.
 * Mantiene in memoria lo storico delle rotte per ogni Tab e coordina le logiche di spostamento (push/pop).
 */
package com.axoo.application.navigation.stack

import android.view.View
import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.PersistedRoute
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.rootRoute
import com.axoo.application.navigation.route.toPersistedRoute
import com.axoo.application.navigation.route.toRoute

/**
 * Rappresenta un singolo piano all'interno della pila di un Tab.
 *
 * Tiene in memoria le istruzioni (Route) e, se necessario, il pezzo di cartapesta 
 * grafico (la View). Quando riapriamo l'app, ripristiniamo le istruzioni ma 
 * aspettiamo a creare la View finché non serve davvero, per risparmiare RAM.
 */
internal data class NavEntry(
    val route: Route,
    var view: View? = null,
)

/**
 * La vera e unica memoria della navigazione.
 * Conosce le destinazioni in cui si trova l'utente e le grafiche ad esse associate.
 */
internal class NavStore(
    initialTab: Tab = Tab.Home,
    snapshot: NavigationSnapshot? = null,
) {
    private val stacks: MutableMap<Tab, ArrayDeque<NavEntry>> =
        Tab.entries.associateWith { ArrayDeque<NavEntry>() }.toMutableMap()

    var currentTab: Tab = initialTab
        private set

    init {
        if (snapshot == null) {
            Tab.entries.forEach(::ensureRoot)
        } else {
            restore(snapshot)
        }
    }

    val currentEntry: NavEntry
        get() = stack(currentTab).last()

    val currentRoute: Route
        get() = currentEntry.route

    fun push(route: Route): NavEntry =
        NavEntry(route).also { stack(currentTab).addLast(it) }

    fun replace(route: Route): NavEntry {
        stack(currentTab).removeLastOrNull()
        return push(route)
    }

    fun switchTab(tab: Tab, target: Route? = null) {
        currentTab = tab
        ensureRoot(tab)
        if (target != null && target != tab.rootRoute) {
            stack(tab).addLast(NavEntry(target))
        }
    }

    fun pop(): NavEntry? {
        val stack = stack(currentTab)
        if (stack.size <= 1) return null
        return stack.removeLast()
    }

    fun canPop(): Boolean = stack(currentTab).size > 1

    fun previousEntry(): NavEntry? {
        val stack = stack(currentTab)
        return if (stack.size > 1) stack.elementAt(stack.size - 2) else null
    }

    fun entries(tab: Tab = currentTab): List<NavEntry> = stack(tab).toList()

    fun snapshot(): NavigationSnapshot =
        NavigationSnapshot(
            currentTab = currentTab,
            stacks = Tab.entries.associateWith { tab ->
                stack(tab).map { it.route.toPersistedRoute() }
            },
        )

    private fun restore(snapshot: NavigationSnapshot) {
        Tab.entries.forEach { tab ->
            val routes = snapshot.stacks[tab]
                .orEmpty()
                .mapNotNull(PersistedRoute::toRoute)
                .ifEmpty { listOf(tab.rootRoute) }
            stack(tab).apply {
                clear()
                routes.forEach { addLast(NavEntry(it)) }
            }
        }
        currentTab = snapshot.currentTab
        ensureRoot(currentTab)
    }

    private fun ensureRoot(tab: Tab) {
        if (stack(tab).isEmpty()) {
            stack(tab).addLast(NavEntry(tab.rootRoute))
        }
    }

    private fun stack(tab: Tab): ArrayDeque<NavEntry> =
        stacks.getOrPut(tab) { ArrayDeque() }
}
