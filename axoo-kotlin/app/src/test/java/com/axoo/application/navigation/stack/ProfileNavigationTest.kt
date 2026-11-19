package com.axoo.application.navigation.stack

import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.PersistedRoute
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileNavigationTest {
    @Test fun `saved campus leaderboard returns to profile after update`() {
        val snapshot = NavigationSnapshot(
            currentTab = Tab.Profile,
            stacks = mapOf(Tab.Profile to listOf(
                PersistedRoute("profile"),
                PersistedRoute("campus_leaderboard"),
            )),
        )

        val restored = NavStore(snapshot = NavigationSnapshot.decode(snapshot.encode()))

        assertEquals(Tab.Profile, restored.currentTab)
        assertEquals(Route.Profile, restored.currentRoute)
        assertFalse(restored.canPop())
        assertEquals(listOf(PersistedRoute("profile")), restored.snapshot().stacks[Tab.Profile])
    }

    @Test fun `profile opened from a comment restores and returns to the original post`() {
        val store = NavStore()
        store.push(Route.PostDetail("post", commentId = "comment"))
        store.push(Route.UserProfile("author"))
        val restored = NavStore(snapshot = NavigationSnapshot.decode(store.snapshot().encode()))
        assertEquals(Route.UserProfile("author"), restored.currentEntry.route)
        restored.pop()
        assertEquals(Route.PostDetail("post", commentId = "comment"), restored.currentEntry.route)
    }

    @Test fun `bio editor restores in the profile tab`() {
        val store = NavStore()
        store.switchTab(Tab.Profile)
        store.push(Route.BioEditor)
        val restored = NavStore(snapshot = NavigationSnapshot.decode(store.snapshot().encode()))
        assertEquals(Tab.Profile, restored.currentTab)
        assertEquals(Route.BioEditor, restored.currentEntry.route)
        restored.pop()
        assertEquals(Route.Profile, restored.currentEntry.route)
    }
}
