package com.axoo.features.events.presentation

import com.axoo.features.events.data.EventsApi
import com.axoo.features.posts.data.MediaApi
import androidx.test.core.app.ActivityScenario
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import android.graphics.Rect
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.events.data.EventsRepository
import com.axoo.features.events.domain.CityEvent
import com.axoo.features.posts.data.PostPhotoRepository
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class EventEditorDialogTest {
    @Test
    fun createOpensAndDiscardConfirmationCanBeCancelledAndConfirmed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                EventEditorDialog(activity, activity, model(activity)).show()
            }
            assertVisible("Crea evento")
            onMain {
                val editor = WindowInspector.getGlobalWindowViews().mapNotNull { it.findViewWithTag<View>("event-editor") }.first()
                val footer = editor.findViewWithTag<View>("event-editor-footer")
                val scroll = editor.findViewWithTag<View>("event-editor-scroll")
                val publish = editor.findViewWithTag<View>("event-save")
                val footerBounds = Rect(); footer.getGlobalVisibleRect(footerBounds)
                val scrollBounds = Rect(); scroll.getGlobalVisibleRect(scrollBounds)
                val publishBounds = Rect(); publish.getGlobalVisibleRect(publishBounds)
                assertTrue("Editor must fill the window", editor.height >= editor.rootView.height * 0.9)
                assertTrue("Publish stays outside the scrolling content", footerBounds.top >= scrollBounds.bottom)
                assertTrue("Publish is visible", !publishBounds.isEmpty)
                assertTrue("Empty poster has a visible picker", windowsText("Aggiungi la locandina").isNotEmpty())
            }
            click("Indietro")
            assertVisible("Uscire dalla modifica?")
            click("Resta")
            assertVisible("Crea evento")
            click("Indietro")
            click("Esci")
            assertHidden("Crea evento")
        }
    }

    @Test
    fun editOpensWithExistingFieldsAndCanBeClosed() {
        val starts = Instant.now().plusSeconds(86400)
        val event = CityEvent(id = "preview", title = "Evento di prova", description = "Descrizione",
            coverUrl = "https://example.test/event.jpg", organizerName = "Admin",
            startsAt = starts.toString(), endsAt = starts.plusSeconds(14400).toString(),
            venue = "Locale", address = "Via Roma 1", entryInfo = "Ingresso libero")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                EventEditorDialog(activity, activity, model(activity), event).show()
            }
            assertVisible("Modifica evento")
            onMain {
                val editor = WindowInspector.getGlobalWindowViews().mapNotNull { it.findViewWithTag<View>("event-editor") }.first()
                assertEquals("Evento di prova", editor.findViewWithTag<TextView>("event-title").text.toString())
                assertEquals("Via Roma 1", editor.findViewWithTag<TextView>("event-address").text.toString())
                assertTrue("No separate preview action", windowsText("Anteprima").isEmpty())
            }
            click("Indietro")
            click("Esci")
        }
    }

    // Public window inspection works with the platform Activity theme and Android 17.
    // These checks exercise dialogs without Espresso's hidden InputManager methods.
    private fun windowsText(value: String): List<TextView> = WindowInspector.getGlobalWindowViews().asReversed()
        .flatMap { it.descendants().filterIsInstance<TextView>().toList() }
        .filter { it.isShown && it.text.toString() == value }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
        }
    }
    private fun onMain(block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync(block)
    }
    private fun assertVisible(text: String) = onMain {
        assertTrue("Expected visible dialog text: $text", windowsText(text).isNotEmpty())
    }
    private fun assertHidden(text: String) = onMain {
        assertTrue("Unexpected visible dialog text: $text", windowsText(text).isEmpty())
    }
    private fun click(text: String) = onMain {
        val control = WindowInspector.getGlobalWindowViews().asReversed()
            .flatMap { it.descendants().toList() }
            .firstOrNull { it.isShown && it.isClickable && ((it is TextView && it.text.toString() == text) || it.contentDescription?.toString() == text) }
        assertFalse("Missing dialog control: $text", control == null)
        assertTrue(requireNotNull(control).performClick())
    }

    private fun model(activity: MainActivity): EventsViewModel {
        val api = Proxy.newProxyInstance(EventsApi::class.java.classLoader, arrayOf(EventsApi::class.java)) { _, method, _ ->
            error("Opening or closing an event form must not call the API: ${method.name}")
        } as EventsApi
        val mediaApi = Proxy.newProxyInstance(MediaApi::class.java.classLoader, arrayOf(MediaApi::class.java)) { _, _, _ ->
            error("Opening or closing an event form must not upload media")
        } as MediaApi
        return EventsViewModel(EventsRepository(api), PostPhotoRepository(activity.applicationContext, mediaApi))
    }
}
