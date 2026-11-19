package com.axoo.application.navigation.stack

import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationsNavigationTest {
    @Test fun `notification tab and target comment survive navigation restoration`() {
        val store = NavStore()
        store.switchTab(Tab.Notifications)
        store.push(Route.PostDetail("post", commentId = "comment"))
        val restored = NavStore(snapshot = NavigationSnapshot.decode(store.snapshot().encode()))
        assertEquals(Tab.Notifications, restored.currentTab)
        assertEquals(listOf(Route.Notifications, Route.PostDetail("post", commentId = "comment")), restored.entries().map { it.route })
    }
}
