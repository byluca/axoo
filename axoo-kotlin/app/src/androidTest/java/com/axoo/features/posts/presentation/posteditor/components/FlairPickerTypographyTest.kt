package com.axoo.features.posts.presentation.posteditor.components

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.presentation.components.flairAppearance
import com.axoo.ui.theme.AppTheme
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlairPickerTypographyTest {
    @Test
    fun categoryOptionsMatchIOSFontSizeAndUseUppercaseLabels() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sheet = NativeFlairPickerSheetView(context, {}, {})
        sheet.bind(selected = null, includeTutti = false)
        val texts = descendants(sheet).filterIsInstance<TextView>()
        val labels = listOf("SENZA CATEGORIA") + kFlairs.map { it.uppercase() }
        for (label in labels) {
            val row = texts.single { it.text.toString() == label }
            assertEquals(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 18f,
                    context.resources.displayMetrics
                ), row.textSize, 0.01f
            )
            assertEquals(context.baloo2Typeface(700), row.typeface)
            val flair = kFlairs.firstOrNull { it.uppercase() == label }
            val appearance = flair?.let(::flairAppearance)
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val color = if (appearance == null) NativeColors.textPrimary else if (dark) appearance.background else appearance.foreground
            assertEquals(color, row.currentTextColor)
            val parent = row.parent as ViewGroup
            (0 until parent.childCount).map(parent::getChildAt).filterIsInstance<ImageView>()
                .forEach { assertEquals(color, it.imageTintList!!.defaultColor) }
        }
        val body = texts.single { it.text.toString() == "Aiuta a capire cosa dice il tuo post." }
        assertEquals(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14f,
                context.resources.displayMetrics
            ), body.textSize, 0.01f
        )
    }

    @Test
    fun firstOpeningShowsEveryCategoryColorInBothThemes() = onMain {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                AppCompatDelegate.setDefaultNightMode(if (mode == Configuration.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                val context = base.createConfigurationContext(Configuration().apply { uiMode = mode })
                NativeColors.initialize(context.resources)
                val sheet = NativeFlairPickerSheetView(context, {}, {})
                sheet.bind(selected = null, includeTutti = false)
                for (flair in kFlairs) {
                    val label = descendants(sheet).filterIsInstance<TextView>().single { it.text.toString() == flair.uppercase() }
                    val appearance = flairAppearance(flair)
                    val expected = if (mode == Configuration.UI_MODE_NIGHT_YES) appearance.background else appearance.foreground
                    assertEquals(expected, label.currentTextColor)
                    val row = label.parent as ViewGroup
                    (0 until row.childCount).map(row::getChildAt).filterIsInstance<ImageView>()
                        .forEach { assertEquals(expected, it.imageTintList!!.defaultColor) }
                }
                sheet.bind(selected = "Meme", includeTutti = false)
                val selected = descendants(sheet).filterIsInstance<TextView>().single { it.text.toString() == "MEME" }
                assertEquals(flairAppearance("Meme").foreground, selected.currentTextColor)
            }
        } finally {
            AppCompatDelegate.setDefaultNightMode(previousMode)
            NativeColors.initialize(AppTheme.wrap(base).resources)
        }
    }

    @Test
    fun noCategorySelectionKeepsAndroidNeutralGray() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sheet = NativeFlairPickerSheetView(context, {}, {})
        sheet.bind(selected = null, includeTutti = false)
        val label = descendants(sheet).filterIsInstance<TextView>().single { it.text.toString() == "SENZA CATEGORIA" }
        val background = (label.parent as View).background as StateListDrawable
        val selected = background.current as GradientDrawable
        assertNotNull(selected.color)
        assertEquals(NativeColors.surface, selected.color!!.defaultColor)
        assertEquals(NativeColors.textPrimary, label.currentTextColor)
    }

    private fun descendants(view: ViewGroup): List<View> = (0 until view.childCount).flatMap { index ->
        val child = view.getChildAt(index)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
