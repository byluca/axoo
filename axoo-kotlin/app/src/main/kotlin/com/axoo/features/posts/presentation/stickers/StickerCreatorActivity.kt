package com.axoo.features.posts.presentation.stickers

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.axoo.features.posts.data.PersonalStickerRepository
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.AppTheme
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface
import com.axoo.ui.theme.dp
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
internal class StickerCreatorActivity : ComponentActivity() {
    @Inject lateinit var repository: PersonalStickerRepository
    private lateinit var crop: StickerCropView
    private lateinit var save: NativeLoadingButton
    private var source: Uri? = null
    private var busy = false
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) finish() else { source = uri; load(uri) }
    }

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppTheme.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        NativeColors.initialize(resources)
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NativeColors.background)
        }
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(20), 0)
            addView(ArrowBackButton(this@StickerCreatorActivity).apply {
                contentDescription = "Indietro"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(this@StickerCreatorActivity).apply {
                text = "Crea sticker"; textSize = 24f
                typeface = baloo2Typeface(700); setTextColor(NativeColors.textPrimary)
            })
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, dp(56)))
        crop = StickerCropView(this).apply { contentDescription = "Ritaglio dello sticker" }
        root.addView(crop, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(20), dp(16), dp(20), 0) })
        root.addView(TextView(this).apply {
            text = "Sposta e ingrandisci"
            textSize = 14f; typeface = interTypeface(400)
            setTextColor(NativeColors.textSecondary)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), 0)
        })
        save = NativeLoadingButton(this).apply { setOnClickListener { create() } }
        root.addView(save, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(20), dp(12), dp(20), dp(20)) })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        save.bind("Crea sticker", enabled = false, loading = false)
        source = savedInstanceState?.getString("source")?.let(Uri::parse)
        source?.let(::load) ?: picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("source", source?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun load(uri: Uri) = lifecycleScope.launch {
        var decoded: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { decoded = decode(uri) }
            crop.image = decoded
            decoded = null
            save.bind("Crea sticker", enabled = true, loading = false)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { Toast.makeText(this@StickerCreatorActivity, "Impossibile aprire questa foto", Toast.LENGTH_LONG).show(); finish() }
        finally { decoded?.recycle() }
    }

    private fun create() {
        if (busy || crop.image == null) return
        busy = true
        crop.isEnabled = false
        save.bind("Crea sticker", enabled = false, loading = true)
        val bitmap = crop.cropped()
        lifecycleScope.launch {
            try {
                val png = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        stream.toByteArray()
                    }
                }
                val sticker = repository.create(png)
                setResult(RESULT_OK, Intent().putExtra("id", sticker.providerId).putExtra("url", sticker.url))
                finish()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = if ((error as? retrofit2.HttpException)?.code() == 409) "La tua raccolta contiene già 100 sticker"
                    else "Sticker non salvato, riprova"
                Toast.makeText(this@StickerCreatorActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                bitmap.recycle()
                busy = false
                crop.isEnabled = true
                save.bind("Crea sticker", enabled = true, loading = false)
            }
        }
    }

    private fun decode(uri: Uri): Bitmap {
        fun stream() = requireNotNull(contentResolver.openInputStream(uri))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val decoded = stream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Foto non leggibile")
        val orientation = runCatching { stream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        return try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() } }
        catch (error: Throwable) { decoded.recycle(); throw error }
    }

    override fun onDestroy() { crop.image?.recycle(); crop.image = null; super.onDestroy() }
}
