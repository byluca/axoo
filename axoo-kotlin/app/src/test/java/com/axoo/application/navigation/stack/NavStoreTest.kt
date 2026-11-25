package com.axoo.application.navigation.stack

import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.toPersistedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStoreTest {
    @Test
    fun switching_tabs_preserves_each_tab_stack() {
        val store = NavStore()

        store.push(Route.PostDetail("post-1"))
        store.switchTab(Tab.Profile)
        store.switchTab(Tab.Home)

        assertEquals(Route.PostDetail("post-1"), store.currentRoute)
        assertEquals(
            listOf(Route.Home, Route.PostDetail("post-1")),
            store.entries().map { it.route },
        )
    }

    @Test
    fun pop_only_affects_current_tab() {
        val store = NavStore()

        store.push(Route.PostDetail("post-1"))
        store.switchTab(Tab.Profile)

        assertEquals(null, store.pop())
        assertEquals(Route.Profile, store.currentRoute)

        store.switchTab(Tab.Home)
        assertEquals(Route.PostDetail("post-1"), store.pop()?.route)
        assertEquals(Route.Home, store.currentRoute)
    }

    @Test
    fun snapshot_roundtrip_restores_current_tab_and_routes() {
        val store = NavStore()
        store.push(Route.PostDetail("post=with,symbols"))
        store.switchTab(Tab.Profile)

        val restored = NavStore(
            snapshot = NavigationSnapshot.decode(store.snapshot().encode()),
        )

        assertEquals(Route.Profile, restored.currentRoute)
        restored.switchTab(Tab.Home)
        assertEquals(Route.PostDetail("post=with,symbols"), restored.currentRoute)
        assertEquals(
            listOf(Route.Home, Route.PostDetail("post=with,symbols")),
            restored.entries().map { it.route },
        )
        assertTrue(restored.entries().all { it.view == null })
    }

    @Test
    fun snapshot_roundtrip_restores_profile_settings_on_profile_stack() {
        val store = NavStore()
        store.switchTab(Tab.Profile)
        store.push(Route.ProfileSettings)

        val restored = NavStore(
            snapshot = NavigationSnapshot.decode(store.snapshot().encode()),
        )

        assertEquals(Tab.Profile, restored.currentTab)
        assertEquals(Route.ProfileSettings, restored.currentRoute)
        assertEquals(
            listOf(Route.Profile, Route.ProfileSettings),
            restored.entries().map { it.route },
        )
    }

    @Test
    fun snapshot_ignores_removed_admin_panel_and_restores_profile_settings() {
        val restored = NavStore(
            snapshot = NavigationSnapshot.decode(
                "current=Profile\nProfile=profile,profile_settings,admin_analytics\n",
            ),
        )

        assertEquals(Tab.Profile, restored.currentTab)
        assertEquals(Route.ProfileSettings, restored.currentRoute)
        assertEquals(
            listOf(Route.Profile, Route.ProfileSettings),
            restored.entries().map { it.route },
        )
    }

    @Test
    fun restored_deep_stack_keeps_every_logical_entry_without_materializing_views() {
        val snapshot = NavigationSnapshot(
            currentTab = Tab.Home,
            stacks = mapOf(
                Tab.Home to listOf(
                    Route.Home.toPersistedRoute(),
                    Route.PostDetail("post-1").toPersistedRoute(),
                    Route.PostDetail("post-2").toPersistedRoute(),
                ),
            ),
        )

        val store = NavStore(snapshot = snapshot)

        assertEquals(3, store.entries().size)
        assertEquals(Route.PostDetail("post-2"), store.currentRoute)
        assertEquals(Route.PostDetail("post-1"), store.previousEntry()?.route)
        assertTrue(store.entries().all { it.view == null })
    }

    @Test
    fun replace_changes_only_the_current_top() {
        val store = NavStore()
        store.push(Route.PostDetail("post-1"))

        store.replace(Route.PostDetail("post-2"))

        assertEquals(
            listOf(Route.Home, Route.PostDetail("post-2")),
            store.entries().map { it.route },
        )
    }

    @Test
    fun tab_target_is_pushed_on_the_selected_tab() {
        val store = NavStore()

        store.switchTab(Tab.Home, Route.PostDetail("post-1"))

        assertTrue(store.canPop())
        assertEquals(Route.PostDetail("post-1"), store.currentRoute)
        assertEquals(Route.PostDetail("post-1"), store.pop()?.route)
        assertFalse(store.canPop())
        assertEquals(Route.Home, store.currentRoute)
    }

    @Test fun `a saved events tab falls back to Home and preserves other stacks`() {
        val snapshot = NavigationSnapshot.decode(
            "current=Events\nHome=home\nProfile=profile,bio_editor\nEvents=events,event_detail?eventId:event-1\n",
        )
        val restored = NavStore(snapshot = snapshot)
        assertEquals(Tab.Home, restored.currentTab)
        assertEquals(Route.Home, restored.currentRoute)
        restored.switchTab(Tab.Profile)
        assertEquals(Route.BioEditor, restored.currentRoute)
    }
}
