package com.axoo.features.posts.presentation.media

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.BuildConfig
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.data.KlipyApi
import com.axoo.features.posts.data.KlipyRepository
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.components.MediaImageView
import com.axoo.features.posts.presentation.media.components.RemoteMediaCellView
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerPanelView
import com.axoo.ui.components.MediaPreviewOverlay
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: reads real KLIPY content without publishing anything to Axoo. */
@RunWith(AndroidJUnit4::class)
class KlipyPickerTest {
    @Test fun directSearchPaginationAndStickerSelection() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("klipy_live") == "true")
        assumeTrue(BuildConfig.KLIPY_API_KEY.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = KlipyRepository(KlipyApi.create(Moshi.Builder().build()), context)
        val first = repository.load(RemoteMediaType.Gif, "ciao", 0, 8)
        assertTrue(first.items.isNotEmpty())
        assertTrue(first.items.all { it.provider == "klipy" })
        first.nextOffset?.let { next ->
            assertEquals(2, next)
            assertTrue(repository.load(RemoteMediaType.Gif, "ciao", next, 8).items.isNotEmpty())
        }
        val stickers = repository.load(RemoteMediaType.Sticker, "ciao", 0, 8)
        assertTrue(stickers.items.isNotEmpty())
        assertTrue(stickers.items.all { it.type == RemoteMediaType.Sticker })
        val memes = repository.load(RemoteMediaType.Meme, "", 0)
        assertTrue(memes.items.isNotEmpty())
        assertTrue(memes.items.all { it.type == RemoteMediaType.Meme && it.provider == "klipy" })
        var selected: RemoteMedia? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var panel: RemoteMediaPickerPanelView
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                panel = RemoteMediaPickerPanelView(activity, { selected = it }, {}, {}, {})
                panel.bind(RemoteMediaState(type = RemoteMediaType.Sticker,
                    query = "ciao", items = stickers.items, nextOffset = stickers.nextOffset))
                activity.setContentView(panel)
            }
            SystemClock.sleep(1000)
            lateinit var cell: RemoteMediaCellView
            val imageField = RemoteMediaCellView::class.java.getDeclaredField("mediaView").apply { isAccessible = true }
            var imageLoaded = false
            for (attempt in 0 until 100) {
                scenario.onActivity {
                    val firstCell = descendants(panel).filterIsInstance<RemoteMediaCellView>().firstOrNull()
                    imageLoaded = firstCell != null && (imageField.get(firstCell) as MediaImageView).drawable != null
                }
                if (imageLoaded) break
                SystemClock.sleep(100)
            }
            assertTrue("The grid preview should finish loading", imageLoaded)
            scenario.onActivity {
                cell = descendants(panel).filterIsInstance<RemoteMediaCellView>().first()
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
                cell.dispatchTouchEvent(down)
                down.recycle()
            }
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
            scenario.onActivity {
                assertTrue("Holding a picker cell opens the preview", MediaPreviewOverlay.isShowing)
                assertNull(selected)
                val source = (imageField.get(cell) as MediaImageView).drawable!!
                if (source.constantState != null) {
                    val overlay = panel.rootView.findViewWithTag<ViewGroup>("media-hold-preview")
                    val preview = descendants(overlay).filterIsInstance<ImageView>().single().drawable
                    assertNotNull("A loaded preview should appear without another download", preview)
                    assertNotSame(source, preview)
                }
                val now = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 20f, 20f, 0)
                cell.dispatchTouchEvent(up)
                up.recycle()
                assertFalse(MediaPreviewOverlay.isShowing)
                assertNull(selected)
                assertEquals("Search KLIPY", descendants(panel).filterIsInstance<EditText>().single().hint)
                cell.performClick()
                assertEquals(stickers.items.first(), selected)
                assertEquals("klipy", selected?.provider)
                assertTrue(selected!!.mediaUrl.startsWith("https://static"))
            }
        }
    }

    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
        val child = group.getChildAt(it)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
}
