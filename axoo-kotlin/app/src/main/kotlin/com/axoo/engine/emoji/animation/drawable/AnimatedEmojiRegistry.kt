package com.axoo.engine.emoji.animation.drawable

import android.content.Context
import android.os.Looper
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.lottie.LottieHost
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Riusa i drawable per chiave e ne gestisce i prestiti alle view.
 * Conserva in cache fino a 24 drawable senza lease attive.
 */
object AnimatedEmojiRegistry {
    private data class Entry(
        val drawable: AnimatedEmojiDrawable,
        val leases: MutableSet<AnimatedEmojiLease>,
    )

    private const val MAX_CACHED_ENTRIES = 24
    private val entries = LinkedHashMap<AnimatedEmojiKey, Entry>(32, 0.75f, true)

    /** Acquisisce un prestito esclusivo per questa coppia host/chiave. */
    fun acquire(
        context: Context,
        key: AnimatedEmojiKey,
        host: LottieHost,
    ): AnimatedEmojiLease {
        checkMainThread()
        val existing = entries[key]
        check(existing?.leases?.none { it.host === host } != false) {
            "Host already owns an active lease for animated emoji ${key.id}"
        }

        val entry = existing ?: Entry(
            drawable = AnimatedEmojiDrawable(
                context = context.applicationContext,
                key = key,
            ),
            leases = Collections.newSetFromMap(IdentityHashMap()),
        ).also {
            entries[key] = it
        }

        val lease = AnimatedEmojiLease(
            key = key,
            host = host,
            drawable = entry.drawable,
            release = ::release,
        )
        entry.leases.add(lease)
        try {
            entry.drawable.attachTo(host)
        } catch (error: Throwable) {
            entry.leases.remove(lease)
            lease.markClosed()
            if (entry.leases.isEmpty() && entries.remove(key, entry)) {
                entry.drawable.dispose()
            }
            throw error
        }
        return lease
    }

    private fun release(lease: AnimatedEmojiLease) {
        checkMainThread()
        if (!lease.markClosed()) return

        val entry = entries[lease.key] ?: return
        if (entry.drawable !== lease.drawable || !entry.leases.remove(lease)) return

        entry.drawable.detachFrom(lease.host)
        if (entry.leases.isNotEmpty()) return
        trimCache()
    }

    private fun trimCache() {
        while (entries.values.count { it.leases.isEmpty() } > MAX_CACHED_ENTRIES) {
            val oldest = entries.entries.firstOrNull { it.value.leases.isEmpty() } ?: return
            entries.remove(oldest.key)
            oldest.value.drawable.dispose()
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AnimatedEmojiRegistry must be used from the main thread"
        }
    }
}

/** Accesso al drawable condiviso; close() restituisce il prestito al registry. */
class AnimatedEmojiLease internal constructor(
    internal val key: AnimatedEmojiKey,
    internal val host: LottieHost,
    val drawable: AnimatedEmojiDrawable,
    private val release: (AnimatedEmojiLease) -> Unit,
) : AutoCloseable {

    var isClosed: Boolean = false
        private set

    override fun close() {
        release(this)
    }

    fun play() {
        if (!isClosed) drawable.requestPlayback(host, true)
    }

    fun pause() {
        if (!isClosed) drawable.requestPlayback(host, false)
    }

    internal fun markClosed(): Boolean {
        if (isClosed) return false
        isClosed = true
        return true
    }
}
