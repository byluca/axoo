package com.axoo.features.posts.presentation.media

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.domain.AupStickers
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.media.components.RemoteMediaCellView
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerPanelView
import com.axoo.ui.components.MediaPreviewOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AupStickerPickerTest {
    @Test fun ownPackRemainsSelectableWhileKlipyLoadsOrFailsAndHoldDoesNotSelect() {
        lateinit var picker: RemoteMediaPickerPanelView
        var selected: RemoteMedia? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                picker = RemoteMediaPickerPanelView(activity, { selected = it }, {}, {}, {})
                picker.bind(RemoteMediaState(type = RemoteMediaType.Sticker, isLoading = true))
                activity.setContentView(picker)
            }
            SystemClock.sleep(400)
            scenario.onActivity {
                val labels = descendants(picker).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue("AUP header missing: $labels, picker ${picker.width}x${picker.height}, attached=${picker.isAttachedToWindow}", labels.contains("AUP"))
                picker.bind(RemoteMediaState(type = RemoteMediaType.Sticker, error = RemoteMediaError.LoadingFailed))
                assertTrue(descendants(picker).filterIsInstance<TextView>().any { it.text.toString().contains("Crea sticker") })
                val cell = descendants(picker).filterIsInstance<RemoteMediaCellView>().first { it.contentDescription != "Crea sticker" }
                cell.performLongClick()
                assertTrue(MediaPreviewOverlay.isShowing)
                assertNull(selected)
                val now = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 20f, 20f, 0)
                cell.dispatchTouchEvent(up)
                up.recycle()
                assertFalse(MediaPreviewOverlay.isShowing)
                assertNull(selected)
                cell.performClick()
                assertEquals(AupStickers.all.first(), selected)
                assertEquals("image/webp", selected?.mimeType)
            }
        }
    }
    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
        val child = group.getChildAt(it)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
}
