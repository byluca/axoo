package com.axoo.ui.sheets

import com.axoo.features.posts.presentation.report.NativeReportSheetView
import com.axoo.features.posts.domain.ReportReason

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.application.navigation.host.OverlayHost
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomSheetContainerTest {
    @Test
    fun optionsSurfaceRendersPureWhite() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: OptionsSheetView
            lateinit var surface: View
            val laidOut = CountDownLatch(1)
            scenario.onActivity { activity ->
                sheet = OptionsSheetView(activity) {}
                sheet.bind(listOf(OptionsSheetAction("Azione di test")), "Opzioni", null)
                sheet.show()
                surface = sheet.dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!
                surface.doOnLayout { laidOut.countDown() }
            }
            assertTrue("Sheet did not finish layout", laidOut.await(3, TimeUnit.SECONDS))
            onMain {
                val background = checkNotNull(surface.background)
                val originalBounds = Rect(background.bounds)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                try {
                    background.setBounds(0, 0, 512, 512)
                    background.draw(Canvas(bitmap))
                    assertEquals("Rendered options surface must be white", Color.WHITE, bitmap.getPixel(256, 256))
                } finally {
                    background.bounds = originalBounds
                    bitmap.recycle()
                    sheet.dismissImmediately()
                }
            }
        }
    }

    private class TestSheet(context: Context, config: BottomSheetConfig = BottomSheetConfig()) :
        BottomSheetContainer(context, config) {
        init { setSheetContent(TextView(context).apply { text = "Contenuto di test" }) }
        fun select(action: () -> Unit) = dismissThen(action)
    }

    @Test
    fun contentsScrollAndAvatarConfigurationHasNoHandleOrDrag() = onMain {
        val sheet = TestSheet(context())
        assertEquals(1, descendants(sheet).filterIsInstance<NestedScrollView>().count())
        assertEquals(1, descendants(sheet).filterIsInstance<BottomSheetDragHandleView>().count())
        val handle = descendants(sheet).filterIsInstance<BottomSheetDragHandleView>().single()
        assertEquals(0xFFD1D1D6.toInt(), (handle.imageTintList ?: handle.supportImageTintList)!!.defaultColor)
        assertTrue(sheet.dialog.behavior.isFitToContents)
        assertTrue(sheet.dialog.behavior.skipCollapsed)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.dialog.behavior.state)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
            sheet.dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!.layoutParams.height)
        val avatar = TestSheet(context(), BottomSheetConfig(
            showsDragHandle = false, dragToDismiss = false, dismissOnScrimClick = false,
        ))
        assertTrue(descendants(avatar).none { it is BottomSheetDragHandleView })
        assertFalse(avatar.dialog.behavior.isDraggable)
        assertFalse(avatar.dialog.behavior.isHideable)
    }

    @Test
    fun mediaViewportUsesNativeHalfAndLargeStatesWithoutAnOuterScrollView() = onMain {
        val sheet = TestSheet(context(), BottomSheetConfig(fillsHeight = true))
        assertTrue(descendants(sheet).none { it is NestedScrollView })
        assertFalse(sheet.dialog.behavior.isFitToContents)
        assertEquals(0.5f, sheet.dialog.behavior.halfExpandedRatio, 0.001f)
        assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, sheet.dialog.behavior.state)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
            sheet.dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!.layoutParams.height)
    }

    @Test
    fun reportCannotBeDraggedAwayDuringSubmissionAndRetryRestoresDismissal() = onMain {
        var submissions = 0
        val sheet = NativeReportSheetView(context(), { submissions++ }, {})
        val texts = descendants(sheet).filterIsInstance<TextView>().toList()
        (texts.single { it.text == ReportReason.Spam.label }.parent as View).performClick()
        val submit = texts.single { it.text == "Invia segnalazione" }
        submit.performClick()
        submit.performClick()
        assertEquals(1, submissions)
        assertFalse(sheet.allowsDismissal)
        assertFalse(sheet.dialog.behavior.isDraggable)
        assertFalse(sheet.dialog.behavior.isHideable)
        sheet.showError("Riprova")
        assertTrue(sheet.allowsDismissal)
        assertTrue(sheet.dialog.behavior.isDraggable)
        assertTrue(sheet.dialog.behavior.isHideable)
        assertTrue(submit.isEnabled)
    }

    @Test
    fun dismissalBeforeShowingNotifiesOnlyOnce() = onMain {
        val sheet = TestSheet(context())
        var dismissals = 0
        sheet.addOnDismissedListener { dismissals++ }
        sheet.dismiss()
        sheet.dismiss()
        sheet.dismissImmediately()
        assertEquals(1, dismissals)
        assertFalse(sheet.dialog.isShowing)
    }

    @Test
    fun modalUsesMaterialWindowAndBackIsBlockedOnlyWhileBusy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = OverlayHost(activity)
                val sheet = TestSheet(activity)
                OverlayHost.presentSheetOn(host, sheet)
                assertEquals(0, host.childCount)
                assertTrue(sheet.dialog.isShowing)
                assertEquals(com.google.android.material.R.id.design_bottom_sheet, (sheet.parent as View).id)
                assertTrue(sheet.dialog.edgeToEdgeEnabled)
                @Suppress("DEPRECATION")
                val navigationBarColor = sheet.dialog.window!!.navigationBarColor
                assertEquals(Color.TRANSPARENT, navigationBarColor)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertFalse(sheet.dialog.window!!.isNavigationBarContrastEnforced)
                }
                sheet.allowsDismissal = false
                sheet.dialog.onBackPressedDispatcher.onBackPressed()
                assertTrue(sheet.dialog.isShowing)
                assertFalse(sheet.dialog.behavior.isHideable)
                sheet.allowsDismissal = true
                sheet.dismissImmediately()
                assertFalse(sheet.dialog.isShowing)
            }
        }
    }

    @Test
    fun selectionRunsOnceAfterTheDialogHasClosed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val completed = CountDownLatch(1)
            val calls = AtomicInteger()
            scenario.onActivity { activity ->
                val sheet = TestSheet(activity)
                sheet.show()
                val action = {
                    assertFalse(sheet.dialog.isShowing)
                    calls.incrementAndGet()
                    completed.countDown()
                    Unit
                }
                sheet.select(action)
                sheet.select(action)
            }
            assertTrue("Selection did not complete", completed.await(3, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun activityDestructionClosesTheDialogAndNotifiesOnce() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var sheet: TestSheet
        val dismissed = AtomicInteger()
        try {
            scenario.onActivity { activity ->
                sheet = TestSheet(activity)
                sheet.addOnDismissedListener { dismissed.incrementAndGet() }
                sheet.presentOn(activity.findViewById(android.R.id.content))
                assertTrue(sheet.dialog.isShowing)
            }
        } finally { scenario.close() }
        onMain {
            assertFalse(sheet.dialog.isShowing)
            assertEquals(1, dismissed.get())
        }
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
