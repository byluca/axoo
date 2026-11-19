package com.axoo.application.navigation.host

import com.axoo.ui.sheets.DismissibleOverlay
import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayHostTest {
    private class Overlay(context: Context) : View(context), DismissibleOverlay {
        val listeners = mutableListOf<() -> Unit>()
        var shows = 0
        override fun show() { shows++ }
        override fun dismiss() { listeners.toList().forEach { it() } }
        override fun addOnDismissedListener(listener: () -> Unit) { listeners += listener }
    }

    @Test
    fun backDismissesOverlaysFromTopToBottomWithoutDuplicatePresentation() = onMain {
        val host = OverlayHost(context())
        val first = Overlay(context())
        val second = Overlay(context())
        host.presentOverlay(first)
        host.presentOverlay(second)
        host.presentOverlay(second)
        assertEquals(2, host.childCount)
        assertEquals(1, second.shows)
        assertTrue(host.dismissTopSheet())
        assertNull(second.parent)
        assertSame(host, first.parent)
        assertTrue(host.dismissTopSheet())
        assertEquals(0, host.childCount)
        assertFalse(host.dismissTopSheet())
    }

    @Test
    fun dismissedOverlayIsRemovedFromTheStackEvenWhenItIsNotOnTop() = onMain {
        val host = OverlayHost(context())
        val first = Overlay(context())
        val second = Overlay(context())
        host.presentOverlay(first)
        assertTrue(OverlayHost.presentOverlayOn(first, second))
        first.dismiss()
        assertEquals(1, host.childCount)
        assertTrue(host.dismissTopSheet())
        assertFalse(host.dismissTopSheet())
        assertEquals(0, host.childCount)
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
