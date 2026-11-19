package com.axoo.ui.sheets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class OverlayCardTest {
    @Test
    fun shortActionsShareARowAndTheCardFitsTheWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var handle: OverlayCard.Handle
            scenario.onActivity { activity ->
                handle = OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Eliminare il post?",
                    message = "Il post verrà rimosso definitivamente.",
                    primaryAction = OverlayCardAction("Elimina", destructive = true) {},
                    secondaryAction = OverlayCardAction("Annulla") {},
                )
            }
            onActionButton("Elimina") { button ->
                val row = button.parent as LinearLayout
                assertEquals(LinearLayout.HORIZONTAL, row.orientation)
                assertEquals(row.getChildAt(0).top, button.top)
                assertTrue(button.height >= button.context.dp(44))
                assertTrue(button.rootView.width <= button.context.dp(320))
                assertEquals(NativeColors.textSecondary, (row.getChildAt(0) as TextView).currentTextColor)
                handle.dismiss()
            }
        }
    }

    @Test
    fun longActionsStackAndKeepTheirWholeLabelVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var handle: OverlayCard.Handle
            scenario.onActivity { activity ->
                handle = OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Vuoi uscire?",
                    message = "Il post non è stato pubblicato.",
                    primaryAction = OverlayCardAction("Esci senza pubblicare") {},
                    secondaryAction = OverlayCardAction("Continua a scrivere") {},
                )
            }
            onActionButton("Esci senza pubblicare") { button ->
                val row = button.parent as LinearLayout
                val label = button as TextView
                assertEquals(LinearLayout.VERTICAL, row.orientation)
                assertTrue(button.top >= row.getChildAt(0).bottom)
                assertEquals(row.width, button.width)
                assertTrue(button.height >= button.context.dp(44))
                assertEquals(label.text.length, label.layout.getLineEnd(label.layout.lineCount - 1))
                assertEquals(0, label.layout.getEllipsisCount(label.layout.lineCount - 1))
                handle.dismiss()
            }
        }
    }

    @Test
    fun materialSurfaceUsesTheCurrentPaletteWithoutAnAccentTint() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var handle: OverlayCard.Handle
            scenario.onActivity { activity ->
                handle = OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Superficie bianca",
                    message = "Il tema Material non deve colorare lo sfondo con l'accento.",
                    primaryAction = OverlayCardAction("Conferma di test") {},
                )
            }
            onActionButton { button ->
                val background = checkNotNull(button.rootView.background)
                val originalBounds = Rect(background.bounds)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                try {
                    background.setBounds(0, 0, 512, 512)
                    background.draw(Canvas(bitmap))
                    assertEquals("Rendered dialog surface must match the palette", NativeColors.panel, bitmap.getPixel(256, 256))
                } finally {
                    background.bounds = originalBounds
                    bitmap.recycle()
                    handle.dismiss()
                }
            }
        }
    }

    @Test
    fun actionRunsOnlyOnceAndOnlyAfterTheDialogWindowIsRemoved() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val completed = CountDownLatch(1)
            val calls = AtomicInteger()
            var dialogRoot: View? = null
            var attachedAtAction = true
            scenario.onActivity { activity ->
                OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Conferma del cambio schermata",
                    message = "Verifica della chiusura prima del cambio schermata.",
                    primaryAction = OverlayCardAction("Conferma di test") {
                        attachedAtAction = dialogRoot?.isAttachedToWindow != false
                        calls.incrementAndGet()
                        completed.countDown()
                    },
                )
            }
            onActionButton { button ->
                dialogRoot = button.rootView
                button.performClick()
                button.performClick()
            }

            assertTrue("Dialog action did not complete", completed.await(3, TimeUnit.SECONDS))
            assertFalse("The old window must be removed before logout starts", attachedAtAction)
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun clickingDuringDismissalDoesNotExecuteAnAction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dismissed = CountDownLatch(1)
            val calls = AtomicInteger()
            lateinit var handle: OverlayCard.Handle
            scenario.onActivity { activity ->
                handle = OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Chiusura di test",
                    message = "Una card già in chiusura non deve accettare altre azioni.",
                    primaryAction = OverlayCardAction("Conferma di test") { calls.incrementAndGet() },
                    onDismissed = { dismissed.countDown() },
                )
            }
            onActionButton { button ->
                handle.dismiss()
                button.performClick()
            }
            assertTrue("Dialog did not close", dismissed.await(3, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(0, calls.get())
        }
    }

    @Test
    fun secondaryActionRunsOnlyOnceAfterDismissal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val completed = CountDownLatch(1)
            val calls = AtomicInteger()
            var dialogRoot: View? = null
            var attachedAtAction = true
            scenario.onActivity { activity ->
                OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Azione secondaria",
                    message = "Anche l'azione secondaria deve partire a finestra chiusa.",
                    primaryAction = OverlayCardAction("Conferma di test", destructive = true) {},
                    secondaryAction = OverlayCardAction("Secondaria di test") {
                        attachedAtAction = dialogRoot?.isAttachedToWindow != false
                        calls.incrementAndGet()
                        completed.countDown()
                    },
                )
            }
            onActionButton { button ->
                assertEquals(NativeColors.destructive, (button as TextView).currentTextColor)
            }
            onActionButton("Secondaria di test") { button ->
                assertEquals(NativeColors.textSecondary, (button as TextView).currentTextColor)
                dialogRoot = button.rootView
                button.performClick()
                button.performClick()
            }
            assertTrue("Secondary action did not complete", completed.await(3, TimeUnit.SECONDS))
            assertFalse(attachedAtAction)
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun nativeBackRespectsTheDismissalSetting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dismissed = CountDownLatch(1)
            val calls = AtomicInteger()
            lateinit var handle: OverlayCard.Handle
            var dialogRoot: View? = null
            scenario.onActivity { activity ->
                handle = OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Indietro bloccato",
                    message = "Indietro non deve chiudere questo dialogo.",
                    primaryAction = OverlayCardAction("Conferma di test") { calls.incrementAndGet() },
                    dismissOnBack = false,
                    onDismissed = { dismissed.countDown() },
                )
            }
            onActionButton { dialogRoot = it.rootView }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            awaitWindowFocus(checkNotNull(dialogRoot))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("Non-cancelable dialog must stay open", dialogRoot?.isAttachedToWindow == true)
                handle.dismiss()
            }
            assertTrue(dismissed.await(3, TimeUnit.SECONDS))
            assertEquals(0, calls.get())

            val backDismissed = CountDownLatch(1)
            scenario.onActivity { activity ->
                OverlayCard.present(
                    anchor = activity.findViewById(android.R.id.content),
                    title = "Indietro consentito",
                    message = "Indietro deve chiudere questo dialogo.",
                    primaryAction = OverlayCardAction("Conferma di test") { calls.incrementAndGet() },
                    onDismissed = { backDismissed.countDown() },
                )
            }
            onActionButton { dialogRoot = it.rootView }
            awaitWindowFocus(checkNotNull(dialogRoot))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue("Native back did not close the dialog", backDismissed.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { assertFalse(dialogRoot?.isAttachedToWindow == true) }
            assertEquals(0, calls.get())
        }
    }

    private fun awaitWindowFocus(root: View) {
        val focused = CountDownLatch(1)
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) focused.countDown()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            if (root.hasWindowFocus()) focused.countDown()
            else root.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        }
        try {
            assertTrue("Dialog did not receive window focus", focused.await(3, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                }
            }
        }
    }

    private fun onActionButton(text: String = "Conferma di test", action: (View) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val button = WindowInspector.getGlobalWindowViews().asSequence()
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .first { it.text == text && it.isClickable }
            action(button)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
