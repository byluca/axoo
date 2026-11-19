package com.axoo.features.posts.presentation.sharing

import android.content.Intent
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.ScrollView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.features.posts.presentation.components.NativePostPresentation
import com.axoo.ui.theme.dp
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostShareTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun darkCardsExportWithLightColorsAndKeepTheirOriginalPalette() {
        instrumentation.runOnMainSync {
            val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val darkContext = context.createConfigurationContext(configuration)
            com.axoo.ui.theme.NativeColors.initialize(darkContext.resources)
            try {
                val post = Post(id = "dark-share", userId = "student", nickname = "Studente", universityId = "uniba",
                    body = "Post condiviso da un’app scura", createdAt = "2026-10-24T10:00:00Z")
                val card = NativePostView(darkContext).apply { bind(post, {}, {}, {}, {}, { _, _ -> }) }
                card.measure(View.MeasureSpec.makeMeasureSpec(366, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                val surface = card.javaClass.getDeclaredField("cardRect").apply { isAccessible = true }.get(card) as RectF
                val sampleX = (surface.left + 3).toInt()
                val darkColor = com.axoo.ui.theme.NativeColors.card
                assertNotEquals(Color.WHITE, darkColor)
                val exported = PostShare.render(card)
                try {
                    val scale = exported.width / 414f
                    val height = exported.height / scale
                    val cardTop = (height - 84f - card.height) / 2
                    assertEquals(Color.WHITE, exported.getPixel(((24f + sampleX) * scale).toInt(),
                        ((cardTop + card.height / 2f) * scale).toInt()))
                    File(context.cacheDir, "post-share-light-preview.png").outputStream().use {
                        exported.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    assertEquals(darkColor, com.axoo.ui.theme.NativeColors.card)
                    val source = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
                    try {
                        card.draw(Canvas(source))
                        assertEquals(darkColor, source.getPixel(sampleX, card.height / 2))
                    } finally { source.recycle() }
                } finally { exported.recycle() }
            } finally { com.axoo.ui.theme.NativeColors.initialize(context.resources) }
        }
    }

    @Test fun longCardsKeepTheirLastPixelsAndBrandingWithoutChangingTheSource() {
        instrumentation.runOnMainSync {
            // Simulates a card extending beyond the viewport: its last green row must survive export.
            val card = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    canvas.drawColor(Color.BLUE)
                    canvas.drawRect(0f, height - 80f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.GREEN })
                }
            }
            card.layout(0, 0, 366, 2200)
            val image = PostShare.render(card)
            try {
                assertEquals(366, card.width)
                assertEquals(2200, card.height)
                assertTrue(image.width <= 1080)
                assertTrue(image.height <= 4096)
                assertEquals(0xFFFF00BF.toInt(), image.getPixel(0, 0))
                assertTrue((0 until image.height).any { image.getPixel(image.width / 2, it) == Color.GREEN })
                assertTrue("The footer must include the white wordmark", (image.height * 9 / 10 until image.height).any { y ->
                    (image.width / 3 until image.width * 2 / 3).any { x -> image.getPixel(x, y) == Color.WHITE }
                })
            } finally { image.recycle() }
        }
    }

    @Test fun postTextIsNotTruncatedAndExportUsesItsFullMeasuredHeight() {
        instrumentation.runOnMainSync {
            val post = Post(id = "share-preview", userId = "student", nickname = "Studente", universityId = "uniba",
                universityName = "UniBa", body = (1..22).joinToString("\n") { "Riga $it: il testo completo del post" },
                createdAt = "2026-10-24T10:00:00Z")
            val card = NativePostView(context).apply { bind(post, {}, {}, {}, {}, { _, _ -> }) }
            card.measure(View.MeasureSpec.makeMeasureSpec(context.dp(366), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val originalHeight = card.height
            val image = PostShare.render(card)
            try {
                assertTrue(image.height > image.width * 1.25)
                assertEquals(originalHeight, card.height)
                File(context.cacheDir, "post-share-preview.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { image.recycle() }
        }
    }

    @Test fun sharedPngCanBeReadAndOnlyTheShareDirectoryIsExposed() = runBlocking<Unit> {
        val directory = File(context.cacheDir, "shared-posts").apply { mkdirs() }
        val old = File(directory, "expired.png").apply { writeText("old"); setLastModified(1) }
        val recent = File(directory, "recent.png").apply { writeText("recent") }
        val image = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            val intent = PostShare.createIntent(context, image)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.shared-posts", uri.authority)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            assertNotNull(decoded)
            assertEquals(20, decoded.width)
            assertEquals(30, decoded.height)
            decoded.recycle()
            assertFalse(old.exists())
            assertTrue(recent.exists())
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.shared-posts", File(context.cacheDir, "private.txt"))
                fail("Unrelated cache files must not be shared")
            } catch (_: IllegalArgumentException) { }
        } finally {
            image.recycle()
            recent.delete()
        }
    }

    @Test fun aLoadedPostOpensTheNativeChooserWithTheBrandedImage() {
        val sent = AtomicReference<Intent>()
        val photo = File(context.cacheDir, "share-test-photo.png")
        val bitmap = Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val post = Post(id = "share-native", userId = "student", nickname = "Studente", universityId = "uniba",
            universityName = "UniBa", body = "Ci vediamo in aula studio? 📚", createdAt = "2026-10-24T10:00:00Z",
            media = ImageMeta(Uri.fromFile(photo).toString(), width = 400, height = 260, type = "photo"))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var card: NativePostView
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val recording = object : ContextWrapper(activity) {
                        override fun startActivity(intent: Intent) { sent.set(intent) }
                    }
                    card = NativePostView(recording).apply { bind(post, {}, {}, {}, {}, { _, _ -> }) }
                    activity.setContentView(FrameLayout(activity).apply {
                        // Another feed tab may retain a copy of the same post outside the viewport.
                        addView(NativePostView(recording).apply {
                            bind(post.copy(body = "Altro tab", media = null), {}, {}, {}, {}, { _, _ -> })
                            translationX = 10_000f
                        }, FrameLayout.LayoutParams(-1, -2))
                        addView(ScrollView(activity).apply { addView(card) }, FrameLayout.LayoutParams(-1, -1))
                    })
                }
                val ready = AtomicReference(false)
                for (attempt in 0..<50) {
                    scenario.onActivity { ready.set(card.width > 0 && card.isShareMediaReady) }
                    if (ready.get()) break
                    Thread.sleep(100)
                }
                assertTrue("The local photo must be ready", ready.get())
                instrumentation.waitForIdleSync()
                Thread.sleep(250)
                scenario.onActivity {
                    val share = card.javaClass.getDeclaredField("shareRect").apply { isAccessible = true }.get(card) as RectF
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                            action, share.centerX(), share.centerY(), 0)
                        try { assertTrue(card.dispatchTouchEvent(event)) } finally { event.recycle() }
                    }
                }
                for (attempt in 0..<50) {
                    if (sent.get() != null) break
                    Thread.sleep(100)
                }
                val chooser = sent.get()
                assertNotNull("Sharing must reach the system chooser", chooser)
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                @Suppress("DEPRECATION")
                val imageIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals("image/png", imageIntent.type)
                @Suppress("DEPRECATION")
                val uri = imageIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    File(context.cacheDir, "post-share-native-preview.png").outputStream().use { input.copyTo(it) }
                }
                val exported = BitmapFactory.decodeFile(File(context.cacheDir, "post-share-native-preview.png").path)
                try {
                    assertTrue("The visible post's photo must be shared, not another tab's copy",
                        (0 until exported.height).any { exported.getPixel(exported.width / 2, it) == Color.CYAN })
                } finally { exported.recycle() }
            }
        } finally { photo.delete() }
    }

    @Test fun shareFitsBesideCommentsInFeedAndDetailAtNarrowWidths() {
        instrumentation.runOnMainSync {
            for (presentation in NativePostPresentation.entries) for (width in listOf(280, 390)) {
                val post = Post(id = "footer", userId = "student", universityId = "uniba", body = "Post",
                    commentsCount = 123456, createdAt = "2026-10-24T10:00:00Z")
                val card = NativePostView(context, presentation).apply { bind(post, {}, {}, {}, {}, { _, _ -> }) }
                card.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                fun rect(name: String) = card.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(card) as RectF
                val comments = rect("commentRect")
                val share = rect("shareRect")
                val menu = rect("menuRect")
                assertEquals(comments.top, share.top, 0.1f)
                assertTrue(share.left > comments.right)
                assertTrue(share.right <= menu.left)
                assertTrue(share.width() > 0)
                assertTrue(share.bottom <= card.height)
            }
        }
    }
}
