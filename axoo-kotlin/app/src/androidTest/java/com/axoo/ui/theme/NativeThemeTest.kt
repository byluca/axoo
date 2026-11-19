package com.axoo.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.R
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.features.posts.presentation.posteditor.components.NativePostEditorTopBarView
import com.axoo.features.home.presentation.components.NativeHomeAppBarView
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.ui.components.AxooMascotCanvasPainter
import com.axoo.ui.components.AxooRefreshLayout
import com.axoo.ui.components.NativeSortTabStrip
import com.axoo.ui.components.ArrowBackButton
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeTest {
    @Test
    fun palettesFollowConfigurationAndKeepTextReadable() = onMain {
        for (night in listOf(false, true, false)) {
            themedContext(night)
            val surfaces = listOf(NativeColors.background, NativeColors.card, NativeColors.panel)
            for (surface in surfaces) {
                assertTrue(ColorUtils.calculateContrast(NativeColors.textPrimary, surface) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(NativeColors.textSecondary, surface) >= 4.5)
            }
            if (night) {
                assertNotEquals(NativeColors.background, NativeColors.card)
            } else {
                assertEquals(NativeColors.white, NativeColors.background)
            }
            assertEquals(NativeColors.white, NativeColors.onAccent)
        }
    }

    @Test
    fun postKeepsItsSurfaceWhileScrollingAndUsesTheCurrentTextColor() = onMain {
        val context = themedContext(night = true)
        val viewport = FrameLayout(context)
        val post = NativePostView(context)
        post.bind(Post(id = "theme-test", userId = "author", universityId = "uniba",
            nickname = "Studente", body = "Test", createdAt = "2026-10-07T10:00:00Z"),
            {}, {}, {}, {}, { _, _ -> })
        viewport.addView(post, FrameLayout.LayoutParams(-1, -2))
        viewport.measure(View.MeasureSpec.makeMeasureSpec(context.dp(390), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(context.dp(900), View.MeasureSpec.EXACTLY))
        viewport.layout(0, 0, viewport.measuredWidth, viewport.measuredHeight)

        fun pixel(yDp: Int): Int {
            val bitmap = Bitmap.createBitmap(post.width, post.height, Bitmap.Config.ARGB_8888)
            return try {
                post.draw(Canvas(bitmap))
                bitmap.getPixel(context.dp(24), context.dp(yDp))
            } finally {
                bitmap.recycle()
            }
        }
        val initial = pixel(80)
        assertEquals(NativeColors.card, initial)
        val bodyLayout = post.javaClass.getDeclaredField("bodyLayout")
            .apply { isAccessible = true }.get(post) as Layout
        assertEquals("Post text must use the current palette on its first draw",
            NativeColors.textPrimary, bodyLayout.paint.color)
        val sameScreenPosition = pixel(120)
        post.translationY = context.dp(40).toFloat()
        assertEquals(sameScreenPosition, pixel(80))
        post.translationY = context.dp(450).toFloat()
        assertEquals(initial, pixel(80))
    }

    @Test
    fun homeAndEditorBarsUseTheCardColorAndSortTabsAreTransparent() = onMain {
        val context = themedContext(night = true)
        val appBar = NativeHomeAppBarView(context)
        assertEquals(NativeColors.textPrimary, ArrowBackButton(context).imageTintList!!.defaultColor)
        assertEquals(NativeColors.card, (appBar.background as ColorDrawable).color)
        val editorBar = NativePostEditorTopBarView(context)
        assertEquals(NativeColors.card, (editorBar.background as ColorDrawable).color)
        val tabs = (0 until appBar.childCount).map(appBar::getChildAt)
            .filterIsInstance<NativeSortTabStrip>().single()
        assertEquals(0, (tabs.background as ColorDrawable).color)
        val bottomBar = NativeBottomNavigationBar(context)
        assertEquals(NativeColors.card,
            (bottomBar.getChildAt(0).background as GradientDrawable).color!!.defaultColor)
    }

    @Test
    fun refreshKeepsTheOriginalMascotAndBackgroundInBothThemes() = onMain {
        fun render(night: Boolean): IntArray {
            val context = themedContext(night)
            val refresh = AxooRefreshLayout(context, View(context)) { false }
            assertEquals(0xFFFF00BF.toInt(), (refresh.background as ColorDrawable).color)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            return try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(0xFFFF00BF.toInt())
                AxooMascotCanvasPainter().draw(canvas, RectF(0f, 0f, 128f, 128f))
                IntArray(128 * 128).also { bitmap.getPixels(it, 0, 128, 0, 0, 128, 128) }
            } finally {
                bitmap.recycle()
            }
        }
        assertArrayEquals(render(night = false), render(night = true))
    }

    private fun themedContext(night: Boolean): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_Axoo)
            .also { NativeColors.initialize(it.resources) }
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } finally {
                NativeColors.initialize(InstrumentationRegistry.getInstrumentation().targetContext.resources)
            }
        }
}
