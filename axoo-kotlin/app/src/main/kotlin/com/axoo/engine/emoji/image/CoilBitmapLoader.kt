package com.axoo.engine.emoji.image

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult

internal object CoilBitmapLoader {
    fun peek(context: Context, data: String, sizePx: Int): Bitmap? =
        context.imageLoader.memoryCache
            ?.get(MemoryCache.Key(cacheKey(data, sizePx)))
            ?.bitmap
            ?.takeUnless(Bitmap::isRecycled)

    fun prefetch(context: Context, data: String, sizePx: Int) {
        if (peek(context, data, sizePx) != null) return
        context.imageLoader.enqueue(request(context, data, sizePx))
    }

    suspend fun load(context: Context, data: String, sizePx: Int): Bitmap? {
        peek(context, data, sizePx)?.let { return it }
        val result = context.imageLoader.execute(request(context, data, sizePx))
        return (result as? SuccessResult)
            ?.drawable
            ?.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888)
    }

    fun enqueue(context: Context, data: String, sizePx: Int, onLoaded: () -> Unit) {
        if (peek(context, data, sizePx) != null) {
            onLoaded()
            return
        }
        context.imageLoader.enqueue(
            request(context, data, sizePx)
                .newBuilder()
                .target(onSuccess = { onLoaded() })
                .build(),
        )
    }

    fun assetData(assetPath: String): String = "file:///android_asset/$assetPath"

    private fun request(context: Context, data: String, sizePx: Int): ImageRequest =
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(cacheKey(data, sizePx))
            .size(sizePx, sizePx)
            .allowHardware(false)
            .build()

    private fun cacheKey(data: String, sizePx: Int): String = "axoo-bitmap:$data@$sizePx"
}
