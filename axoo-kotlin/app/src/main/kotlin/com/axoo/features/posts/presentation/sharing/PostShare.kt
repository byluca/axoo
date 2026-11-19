package com.axoo.features.posts.presentation.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.NativeColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Shares the complete card, without changing the feed or uploading a screenshot. */
internal object PostShare {
    private var job: Job? = null

    fun share(card: NativePostView) {
        if (job?.isActive == true) return
        if (!card.isAttachedToWindow || card.width <= 0 || card.height <= 0) return
        if (!card.isShareMediaReady) {
            Toast.makeText(card.context, "Attendi il caricamento del post e riprova", Toast.LENGTH_SHORT).show()
            return
        }
        val scope = card.findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        job = scope.launch {
            try {
                val image = render(card)
                val intent = try { createIntent(card.context, image) } finally { image.recycle() }
                if (card.isAttachedToWindow) {
                    card.context.startActivity(Intent.createChooser(intent, "Condividi post"))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (card.isAttachedToWindow) Toast.makeText(card.context,
                    "Impossibile condividere il post. Riprova.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal fun render(card: View): Bitmap {
        require(card.width > 0 && card.height > 0)
        val unit = card.width / 366f
        val padding = 24f * unit
        val footer = 84f * unit
        val width = card.width + padding * 2
        val height = maxOf(width * 1.25f, card.height + padding * 2 + footer)
        val scale = minOf(1080f / width, 4096f / height)
        val bitmap = Bitmap.createBitmap((width * scale).roundToInt(), (height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFFF00BF.toInt())
            canvas.scale(scale, scale)
            val top = (height - footer - card.height) / 2
            canvas.save()
            canvas.translate(padding, top)
            canvas.clipRect(0, 0, card.width, card.height)
            NativeColors.withLightColors(card.context) { card.draw(canvas) }
            canvas.restore()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = card.context.baloo2Typeface(800)
                textSize = 40f * unit
                textAlign = Paint.Align.CENTER
            }
            val centerY = height - footer / 2
            canvas.drawText("Axoo", width / 2, centerY - (paint.ascent() + paint.descent()) / 2, paint)
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    internal suspend fun createIntent(context: Context, image: Bitmap): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared-posts").apply { mkdirs() }
        val expired = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expired }?.forEach { it.delete() }
        val file = File(directory, "axoo-${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared-posts", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Post Axoo", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }
}
