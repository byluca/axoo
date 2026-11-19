package com.axoo.application.navigation.stack

import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import org.junit.Assert.*
import org.junit.Test

class EventsNavigationTest {
    @Test fun `events and detail restore separately from feed and post creation`() {
        val store = NavStore()
        store.switchTab(Tab.Events)
        assertEquals(Route.Events, store.currentRoute)
        store.push(Route.EventDetail("event-id"))
        store.switchTab(Tab.Home)
        assertEquals(Route.Home, store.currentRoute)
        val restored = NavStore(snapshot = NavigationSnapshot.decode(store.snapshot().encode()))
        restored.switchTab(Tab.Events)
        assertEquals(Route.EventDetail("event-id"), restored.currentRoute)
        restored.pop()
        assertEquals(Route.Events,restored.currentRoute)
        restored.switchTab(Tab.Create)
        assertEquals(Route.PostEditor,restored.currentRoute)
    }
}
