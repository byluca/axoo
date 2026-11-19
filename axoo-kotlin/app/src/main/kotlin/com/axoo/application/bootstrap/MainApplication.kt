/*
 * MainApplication.kt
 *
 * Prepara il motore dell'app prima di mostrare lo schermo.
 *
 * Cosa fa questa classe:
 * 1. Prepara immagini ed emoji.
 * 2. Configura il pool usato per renderizzare le animazioni.
 */
package com.axoo.application.bootstrap

import com.axoo.ui.theme.NativeColors
import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder /* Usiamo la libreria Coil per leggere svg,gif,jpeg e altre cose... */
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.intercept.Interceptor
import coil.request.CachePolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.axoo.core.audio.AppSounds
import com.axoo.core.push.PushNotifications
import com.axoo.engine.emoji.catalog.EmojiData
import dagger.hilt.android.HiltAndroidApp /* serve per caricare in automatico le dipendenze */
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var pushNotifications: PushNotifications

    /* Prima cosa che parte: prepara le cose base. */
    override fun onCreate() {
        super.onCreate()
        NativeColors.initialize(resources)
        pushNotifications.createChannel()
        AppSounds.initialize(this)
        EmojiData.configure(this)

    }

    /* Prepara il sistema per caricare le immagini e leggere gli SVG. */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_media_cache"))
                    .maxSizeBytes(128L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(Interceptor { chain ->
                    val host = chain.request.data.toString().toHttpUrlOrNull()?.host
                    val request = if (host in setOf("static.klipy.com", "static1.klipy.com", "static2.klipy.com")) {
                        chain.request.newBuilder()
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED).build()
                    } else chain.request
                    chain.proceed(request)
                })
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

}
