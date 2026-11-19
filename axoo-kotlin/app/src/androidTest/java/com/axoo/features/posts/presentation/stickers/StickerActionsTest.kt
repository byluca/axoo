package com.axoo.features.posts.presentation.stickers

import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.components.NativeCommentView
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import com.axoo.ui.sheets.OptionsSheetView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class StickerActionsTest {
    private val sticker = ImageMeta("https://example.test/sticker.png", width = 512, height = 512,
        type = "sticker", provider = "axoo", providerId = "3359c318-5414-4ba8-a15a-5fc0c905d817", mimeType = "image/png")

    @Test fun tappingAnotherAuthorsPostStickerOffersSave() = checkSave { activity ->
        NativePostView(activity).apply {
            bind(Post("p1", "other", "Altro", "uniba", media = sticker, createdAt = "2026-10-24T10:00:00Z"), {}, {}, {}, {}, { _, _ -> })
        }
    }

    @Test fun tappingAnotherAuthorsCommentStickerOffersSave() = checkSave { activity ->
        NativeCommentView(activity).apply {
            bind(CommentUiModel(Comment(id = "c1", postId = "p1", userId = "other", media = sticker, createdAt = "2026-10-24T10:00:00Z")))
        }
    }

    private fun checkSave(make: (MainActivity) -> View) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var source: View
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                source = make(activity)
                activity.setContentView(FrameLayout(activity).apply { addView(source, FrameLayout.LayoutParams(-1, -2)) })
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                val rect = source.javaClass.getDeclaredField("mediaRect").apply { isAccessible = true }.get(source) as RectF
                assertFalse(rect.isEmpty)
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(now, now, action, rect.centerX(), rect.centerY(), 0).also { source.dispatchTouchEvent(it); it.recycle() }
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val views = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "Salva nei miei sticker" && it.isShown })
                views.filterIsInstance<OptionsSheetView>().forEach { it.dismiss() }
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}
