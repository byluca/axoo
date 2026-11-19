package com.axoo.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.axoo.R
import java.util.concurrent.ConcurrentHashMap

/** Short UI sounds decoded once at app startup for immediate playback. */
object AppSounds {
    private val loadedSamples = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var soundPool: SoundPool? = null
    private var commentSentId = 0

    fun initialize(context: Context) {
        if (soundPool != null) return
        synchronized(this) {
            if (soundPool != null) return
            val appContext = context.applicationContext
            val pool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loadedSamples += sampleId
            }
            commentSentId = pool.load(appContext, R.raw.comment_sent, 1)
            soundPool = pool
        }
    }

    fun commentSent(context: Context) {
        initialize(context)
        play(context, commentSentId, COMMENT_VOLUME)
    }

    private fun play(context: Context, sampleId: Int, volume: Float) {
        if (sampleId == 0 || sampleId !in loadedSamples) return
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        soundPool?.play(sampleId, volume, volume, PLAY_PRIORITY, 0, 1f)
    }

    private const val COMMENT_VOLUME = 0.30f
    private const val PLAY_PRIORITY = 1
}
