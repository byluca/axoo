package com.axoo.features.posts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.presentation.posteditor.PostMediaKind
import com.axoo.features.posts.presentation.posteditor.SelectedPostMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class PostPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MediaApi,
) {
    private val directory get() = File(context.cacheDir, "post-photos").apply { mkdirs() }

    suspend fun prepare(uri: Uri): SelectedPostMedia = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        fun stream() = requireNotNull(resolver.openInputStream(uri)) { "Foto non disponibile" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato della foto non supportato" }
        val orientation = stream().use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val decoded = stream().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Impossibile leggere la foto")
        try {
            val scale = minOf(1f, 1600f / maxOf(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
                postScale(scale, scale)
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(rotated, 0f, 0f, null) }
                    val file = File(directory, "${UUID.randomUUID()}.jpg")
                    try {
                        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
                        require(file.length() <= 3 * 1024 * 1024) { "Foto troppo grande, scegli un'immagine più piccola" }
                        ensureActive()
                        val url = Uri.fromFile(file).toString()
                        SelectedPostMedia(PostMediaKind.Photo, url, url, width = output.width, height = output.height, mimeType = "image/jpeg")
                    } catch (error: Throwable) {
                        file.delete()
                        throw error
                    }
                } finally { output.recycle() }
            } finally { if (rotated !== decoded) rotated.recycle() }
        } finally { decoded.recycle() }
    }

    suspend fun upload(media: SelectedPostMedia): ImageMeta = withContext(Dispatchers.IO) {
        val file = localFile(media) ?: error("Foto locale non disponibile: selezionala di nuovo")
        require(file.isFile) { "Foto locale non disponibile: selezionala di nuovo" }
        api.uploadPostPhoto(MultipartBody.Part.createFormData("photo", "photo.jpg", file.asRequestBody("image/jpeg".toMediaType())))
    }

    fun discard(media: SelectedPostMedia?) { media?.let(::localFile)?.delete() }

    private fun localFile(media: SelectedPostMedia): File? {
        if (media.kind != PostMediaKind.Photo) return null
        val uri = Uri.parse(media.url)
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }
}
