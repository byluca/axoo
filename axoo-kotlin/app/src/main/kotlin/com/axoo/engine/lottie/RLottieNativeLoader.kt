package com.axoo.engine.lottie

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object RLottieNativeLoader {

    // Carica l'animazione in memoria tramite la libreria nativa.
    suspend fun load(
        assetPath: String,
        context: Context,
        instanceKey: String = "$assetPath#${System.nanoTime()}",
    ): RLottieAnimation? {
        if (assetPath.isBlank()) return null
        var created: RLottieAnimation? = null
        try {
            return withContext(RLottieRenderPool) {
                try {
                    val animationData = readAnimationJson(context, assetPath) ?: return@withContext null
                    ensureActive()
                    val ptr = RLottieLib.createFromData(animationData, instanceKey)
                    if (ptr == 0L) return@withContext null

                    val metadata = RLottieLib.getInfo(ptr)
                    if (metadata == null) {
                        RLottieLib.destroy(ptr)
                        return@withContext null
                    }

                    val totalFrames = metadata[0].toInt()
                    val frameRate = metadata[1]
                    val width = metadata[2].toInt()
                    val height = metadata[3].toInt()
                    RLottieAnimation(
                        assetPath = assetPath,
                        totalFrames = totalFrames,
                        frameRate = frameRate,
                        width = width,
                        height = height,
                        nativePointer = ptr,
                    ).also { created = it }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "Failed to load native animation $assetPath", error)
                    null
                }
            }
        } catch (error: CancellationException) {
            // withContext may cancel delivery after native creation completed.
            created?.close()
            throw error
        }
    }

    // Legge e decomprime il file fisico dal dispositivo.
    private fun readAnimationJson(context: Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).use { input ->
                java.util.zip.GZIPInputStream(input)
                    .bufferedReader()
                    .use { it.readText() }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to read animation asset $assetPath", error)
            null
        }
    }

    private const val TAG = "RLottieNativeLoader"
}
