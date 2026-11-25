package com.axoo.features.posts.presentation.stickers

import com.axoo.features.posts.data.MediaApi
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.data.PersonalStickerRepository
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.features.posts.presentation.media.components.RemoteMediaCellView
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerPanelView
import com.axoo.ui.components.NativeLoadingButton
import com.squareup.moshi.Moshi
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(AndroidJUnit4::class)
class StickerCreationFlowTest {
    @Test fun firstCellOpensPhotoCropUploadsPNGAndReturnsAReusableSticker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "shared-posts/sticker-fixture.png").apply { parentFile!!.mkdirs() }
        val original = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        Canvas(original).apply {
            drawRect(0f, 0f, 200f, 400f, Paint().apply { color = Color.BLUE })
            drawRect(600f, 0f, 800f, 400f, Paint().apply { color = Color.MAGENTA })
        }
        file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared-posts", file)
        val photo = instrumentation.addMonitor(IntentFilter(MediaStore.ACTION_PICK_IMAGES).apply { addDataType("image/*") },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        val creatorMonitor = instrumentation.addMonitor(StickerCreatorActivity::class.java.name, null, false)
        val selected = AtomicReference<RemoteMedia>()
        val body = AtomicReference<ByteArray>()
        val id = UUID.randomUUID().toString()
        val json = """{"url":"https://example.test/$id.png","width":512,"height":512,"type":"sticker","mime_type":"image/png","provider":"axoo","provider_id":"$id"}"""
        val api = Retrofit.Builder().baseUrl("https://example.test/")
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                body.set(buffer.readByteArray())
                assertEquals("/media/stickers", request.url.encodedPath)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(201).message("Created")
                    .body(json.toResponseBody("application/json".toMediaType())).build()
            }.build()).addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build().create(MediaApi::class.java)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var picker: RemoteMediaPickerPanelView
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    picker = RemoteMediaPickerPanelView(activity, { selected.set(it) }, {}, {}, {})
                    picker.bind(RemoteMediaState(type = RemoteMediaType.Sticker))
                    activity.setContentView(picker)
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(300)
                capture("sticker-picker-preview.png")
                scenario.onActivity {
                    descendants(picker).filterIsInstance<RemoteMediaCellView>().first { it.contentDescription == "Crea sticker" }.performClick()
                }
                val creator = instrumentation.waitForMonitorWithTimeout(creatorMonitor, 5000) as? StickerCreatorActivity
                assertNotNull("Create tile must open the shared creator", creator)
                instrumentation.runOnMainSync { creator!!.repository = PersonalStickerRepository(api) }
                val ready = AtomicReference(false)
                repeat(50) {
                    if (!ready.get()) {
                        instrumentation.runOnMainSync { ready.set(descendants(creator!!.window.decorView as ViewGroup).filterIsInstance<NativeLoadingButton>().first().isEnabled) }
                        if (!ready.get()) Thread.sleep(100)
                    }
                }
                assertTrue("Photo must be loaded before saving", ready.get())
                instrumentation.waitForIdleSync()
                Thread.sleep(300)
                capture("sticker-crop-preview.png")
                instrumentation.runOnMainSync {
                    descendants(creator!!.window.decorView as ViewGroup).filterIsInstance<NativeLoadingButton>().first().performClick()
                }
                repeat(50) { if (selected.get() == null) Thread.sleep(100) }
                val sticker = selected.get()
                assertNotNull("Created sticker must return to the picker", sticker)
                assertEquals(id, sticker.id)
                assertEquals("axoo", sticker.provider)
                assertEquals("image/png", sticker.mimeType)
                assertEquals(RemoteMediaType.Sticker, sticker.type)
                val multipart = body.get()
                val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                val start = (0..multipart.size-pngSignature.size).first { offset -> pngSignature.indices.all { multipart[offset+it] == pngSignature[it] } }
                val decoded = BitmapFactory.decodeByteArray(multipart, start, multipart.size-start)!!
                assertEquals(512, decoded.width); assertEquals(512, decoded.height)
                assertEquals(Color.CYAN, decoded.getPixel(256, 256))
                assertEquals(Color.CYAN, decoded.getPixel(0, 0))
                assertEquals(Color.CYAN, decoded.getPixel(511, 511))
                decoded.recycle()
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    picker.bind(RemoteMediaState(type = RemoteMediaType.Sticker, personalStickers = listOf(sticker)))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    selected.set(null)
                    descendants(picker).filterIsInstance<RemoteMediaCellView>().first { it.contentDescription == "Sticker personale" }.performClick()
                    assertEquals(id, selected.get().id)
                }
            }
        } finally {
            instrumentation.removeMonitor(photo)
            instrumentation.removeMonitor(creatorMonitor)
            file.delete()
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
        val child = group.getChildAt(it)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
}
