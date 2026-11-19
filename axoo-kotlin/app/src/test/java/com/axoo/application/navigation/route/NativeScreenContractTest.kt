package com.axoo.application.navigation.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeScreenContractTest {
    private class RecordingScreen(
        override val route: Route = Route.Home,
    ) : NativeScreen {
        val events = mutableListOf<String>()

        override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
            events += "start:$isOpening:$isBackward"
        }

        override fun onInteractiveTransitionStart(isOpening: Boolean) {
            events += "interactive:$isOpening"
        }

        override fun onScreenFullyVisible() {
            events += "fully"
        }

        override fun onScreenHidden() {
            events += "hidden"
        }

        override fun onScreenDestroyed() {
            events += "destroyed"
        }
    }

    @Test
    fun lifecycle_methods_default_to_noop_on_minimal_implementation() {
        val screen = object : NativeScreen {
            override val route: Route = Route.Home
        }
        // Should not throw — confirms the default no-op contract.
        screen.onScreenFullyVisible()
        assertTrue(true)
    }

    @Test
    fun phase1_lifecycle_order_for_push_matches_spec() {
        // Documents the expected dispatch order for a push transition.
        // The host implementation calls these in this exact sequence; this
        // test mirrors that ordering against a fake screen.
        val outgoing = RecordingScreen(Route.Home)
        val incoming = RecordingScreen(Route.PostDetail("p1"))

        outgoing.onScreenTransitionStart(isOpening = false, isBackward = false)
        incoming.onScreenTransitionStart(isOpening = true, isBackward = false)
        // animator finishes
        outgoing.onScreenHidden()
        incoming.onScreenFullyVisible()

        assertEquals(
            listOf(
                "start:false:false",
                "hidden",
            ),
            outgoing.events,
        )
        assertEquals(
            listOf(
                "start:true:false",
                "fully",
            ),
            incoming.events,
        )
    }

    @Test
    fun interactive_back_has_a_distinct_lifecycle_signal() {
        val outgoing = RecordingScreen(Route.PostDetail("p1"))
        val incoming = RecordingScreen(Route.Home)

        outgoing.onInteractiveTransitionStart(isOpening = false)
        incoming.onInteractiveTransitionStart(isOpening = true)

        assertEquals(listOf("interactive:false"), outgoing.events)
        assertEquals(listOf("interactive:true"), incoming.events)
    }

    @Test
    fun interactive_back_defaults_to_the_regular_backward_transition() {
        val events = mutableListOf<String>()
        val screen = object : NativeScreen {
            override val route: Route = Route.Home

            override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
                events += "start:$isOpening:$isBackward"
            }
        }

        screen.onInteractiveTransitionStart(isOpening = true)

        assertEquals(listOf("start:true:true"), events)
    }
}
