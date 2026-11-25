package com.axoo.features.posts.presentation.posteditor

import android.graphics.Rect
import android.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerSheet
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerPanelView
import com.axoo.features.posts.presentation.posteditor.components.NativePostEditorView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class NativePostEditorMediaSheetTest {
    @Test
    fun nativeDismissalUpdatesEditorOnceAndMediaCanBeReopened() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var editor: NativePostEditorView
            val dismissals = AtomicInteger()
            val closed = CountDownLatch(1)
            scenario.onActivity { activity ->
                editor = NativePostEditorView(activity, ComposerController(activity)) { _, _ -> }
                activity.addContentView(editor, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                editor.setEventSink { event ->
                    if (event == PostEditorEvent.RemoteMediaPickerDismissed) {
                        dismissals.incrementAndGet()
                        closed.countDown()
                    }
                }
                val cached = RemoteMediaState(items = listOf(RemoteMedia(
                    id = "cached-preview", previewUrl = "file:///missing-test-preview.gif",
                    mediaUrl = "file:///missing-test-original.gif", width = 120, height = 120,
                    title = "Cached GIF", type = RemoteMediaType.Gif,
                )))
                editor.showRemotePicker(cached)
                editor.showRemotePicker(cached)
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val sheets = mediaSheets()
                assertEquals("Rebinding must not open a second dialog", 1, sheets.size)
                val behavior = BottomSheetBehavior.from(sheets.single())
                assertFalse(behavior.isFitToContents)
                assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, behavior.state)
                val sheet = sheets.single()
                val viewport = sheet.parent as View
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, sheet.layoutParams.height)
                assertTrue("Media sheet must fill its viewport, not just the header", sheet.height > viewport.height * 0.8f)
                val grid = descendants(sheet).filterIsInstance<RecyclerView>().single()
                val panel = descendants(sheet).filterIsInstance<RemoteMediaPickerPanelView>().single()
                assertTrue("Material is the only sheet surface; search has no surrounding pill", panel.background is ColorDrawable)
                assertNull("No shadow around the inner media panel", panel.foreground)
                assertEquals(1, grid.adapter!!.itemCount)
                assertTrue("Cached media must produce visible rows on the first layout", grid.childCount > 0)
                val visibleGrid = Rect()
                assertTrue("Media grid must be visible", grid.getGlobalVisibleRect(visibleGrid))
                assertTrue("Half-expanded grid must not collapse to a thin strip", visibleGrid.height() > viewport.height / 4)
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            assertTrue("Native dismissal did not reach the editor", closed.await(3, TimeUnit.SECONDS))
            assertEquals(1, dismissals.get())
            scenario.onActivity {
                editor.showRemotePicker(RemoteMediaState(type = RemoteMediaType.Meme, isLoading = true))
                assertEquals(1, mediaSheets().size)
                editor.hideRemotePicker()
                editor.release()
                (editor.parent as ViewGroup).removeView(editor)
            }
            instrumentation.waitForIdleSync()
            assertEquals("Explicit hide must not emit a second user-dismissal event", 1, dismissals.get())
        }
    }

    @Test
    fun expandedGifAndStickerSheetsDrawUnderTransparentSystemNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (mode in listOf(RemoteMediaType.Gif, RemoteMediaType.Sticker)) {
                lateinit var sheet: RemoteMediaPickerSheet
                lateinit var surface: FrameLayout
                val expanded = CountDownLatch(1)
                scenario.onActivity { activity ->
                    sheet = RemoteMediaPickerSheet(activity,
                        onMediaSelected = {}, onSearchQueryChanged = {},
                        onLoadMore = {}, onRetry = {}, onDismissed = {})
                    sheet.bind(RemoteMediaState(type = mode, isLoading = true))
                    sheet.show()
                    surface = sheet.dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!
                    sheet.dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            if (newState == BottomSheetBehavior.STATE_EXPANDED) expanded.countDown()
                        }
                    })
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { sheet.dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
                assertTrue("Media sheet did not expand", expanded.await(3, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    try {
                        val window = sheet.dialog.window!!
                        @Suppress("DEPRECATION")
                        val color = window.navigationBarColor
                        assertEquals(Color.TRANSPARENT, color)
                        @Suppress("DEPRECATION")
                        val divider = window.navigationBarDividerColor
                        assertEquals(Color.TRANSPARENT, divider)
                        assertFalse(window.isNavigationBarContrastEnforced)
                        assertTrue(androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
                        assertEquals("No opaque band below the media content", 0, surface.paddingBottom)
                        val panel = descendants(surface).filterIsInstance<RemoteMediaPickerPanelView>().single()
                        val grid = descendants(panel).filterIsInstance<RecyclerView>().single()
                        val gridBounds = Rect()
                        grid.getDrawingRect(gridBounds)
                        surface.offsetDescendantRectToMyCoords(grid, gridBounds)
                        assertEquals(surface.height, gridBounds.bottom)
                        val navigationInset = ViewCompat.getRootWindowInsets(panel)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        assertTrue("Last media row stays reachable above system controls", grid.paddingBottom >= navigationInset)
                        assertFalse(grid.clipToPadding)
                    } finally { sheet.dismissImmediately() }
                }
            }
        }
    }

    private fun mediaSheets(): List<FrameLayout> =
        WindowInspector.getGlobalWindowViews().asSequence()
            .flatMap(::descendants).filterIsInstance<FrameLayout>()
            .filter { it.id == com.google.android.material.R.id.design_bottom_sheet }
            .filter { descendants(it).any { child -> child is RemoteMediaPickerPanelView } }
            .toList()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
