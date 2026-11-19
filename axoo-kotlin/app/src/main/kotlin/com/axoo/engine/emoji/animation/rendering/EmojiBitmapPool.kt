package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import java.util.ArrayDeque

/**
 * Riusa bitmap per dimensione, con un limite di 8 MB.
 * Accesso sincronizzato; le bitmap eccedenti o non riutilizzabili vengono riciclate.
 */
internal object EmojiBitmapPool {
    private const val MAX_POOLED_BYTES = 8 * 1024 * 1024

    private data class Key(
        val width: Int,
        val height: Int,
    )

    private val lock = Any()

    private val buckets = LinkedHashMap<Key, ArrayDeque<Bitmap>>(16, 0.75f, true)
    private var pooledBytes = 0

    fun acquire(widthPx: Int, heightPx: Int): Bitmap {
        val key = Key(widthPx, heightPx)
        synchronized(lock) {
            val bucket = buckets[key]
            while (bucket != null && bucket.isNotEmpty()) {
                val bitmap = bucket.removeFirst()
                pooledBytes -= bitmap.byteCount
                if (isReusable(bitmap, key)) {
                    if (bucket.isEmpty()) buckets.remove(key)
                    return bitmap
                }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            if (bucket?.isEmpty() == true) buckets.remove(key)
        }
        return createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    }

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = Key(bitmap.width, bitmap.height)

        if (!isReusable(bitmap, key)) {
            bitmap.recycle()
            return
        }

        synchronized(lock) {
            buckets.getOrPut(key) { ArrayDeque() }.addLast(bitmap)
            pooledBytes += bitmap.byteCount

            trimToSizeLocked(MAX_POOLED_BYTES)
        }
    }

    private fun trimToSizeLocked(maxBytes: Int) {
        while (pooledBytes > maxBytes && buckets.isNotEmpty()) {
            val iterator = buckets.entries.iterator()
            val entry = iterator.next()
            val bucket = entry.value
            if (bucket.isEmpty()) {
                iterator.remove()
                continue
            }
            val victim = bucket.removeFirst()
            pooledBytes -= victim.byteCount
            victim.recycle()
            if (bucket.isEmpty()) iterator.remove()
        }
    }

    private fun isReusable(bitmap: Bitmap, key: Key): Boolean =
        !bitmap.isRecycled &&
                bitmap.isMutable &&
                bitmap.config == Bitmap.Config.ARGB_8888 &&
                bitmap.width == key.width &&
                bitmap.height == key.height
}
