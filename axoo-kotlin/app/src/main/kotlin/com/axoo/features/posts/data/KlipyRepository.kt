package com.axoo.features.posts.data

import android.content.Context
import com.axoo.BuildConfig
import com.axoo.features.posts.data.dto.KlipyFile
import com.axoo.features.posts.data.dto.KlipyResponse
import com.axoo.features.posts.data.dto.KlipyShareRequest
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class KlipyRepository internal constructor(
    private val api: KlipyApi,
    private val customerId: String,
    private val apiKey: String,
) {
    @Inject constructor(api: KlipyApi, @ApplicationContext context: Context) :
        this(api, customerId(context), BuildConfig.KLIPY_API_KEY)

    private val shares = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selections = LinkedHashMap<String, String>()

    suspend fun load(type: RemoteMediaType, query: String, offset: Int, limit: Int = PAGE_SIZE): RemoteMediaPage {
        if (apiKey.isBlank()) throw IOException("KLIPY non configurato")
        val collection = when (type) {
            RemoteMediaType.Gif -> "gifs"
            RemoteMediaType.Sticker -> "stickers"
            RemoteMediaType.Meme -> "static-memes"
        }
        val response = api.list(apiKey,
            collection,
            if (query.isBlank()) "trending" else "search",
            buildMap {
                put("page", offset.coerceAtLeast(1).toString())
                put("per_page", limit.coerceIn(8, 50).toString())
                put("customer_id", customerId)
                put("locale", Locale.getDefault().country.lowercase(Locale.ROOT).ifBlank { "it" })
                if (type != RemoteMediaType.Meme) put("format_filter", if (type == RemoteMediaType.Gif) "gif,webp,jpg" else "gif,webp")
                put("content_filter", "high")
                if (query.isNotBlank()) put("q", query)
            })
        return response.toRemoteMediaPage(type, query)
    }

    @Synchronized
    fun rememberSelection(media: RemoteMedia) {
        if (media.provider != "klipy") return
        selections[media.id] = media.searchQuery
        while (selections.size > 100) selections.remove(selections.keys.first())
    }

    fun reportShare(media: ImageMeta?) {
        if (media?.provider != "klipy") return
        val slug = media.providerId ?: return
        val query = synchronized(this) { selections.remove(slug) } ?: return
        shares.launch {
            try {
                api.share(apiKey, when (media.type) { "meme" -> "static-memes"; "sticker" -> "stickers"; else -> "gifs" },
                    slug, KlipyShareRequest(customerId, query))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Analytics never change the result of a published post or comment. */ }
        }
    }

    companion object {
        const val PAGE_SIZE = 30

        private fun customerId(context: Context): String {
            val preferences = context.getSharedPreferences("klipy", Context.MODE_PRIVATE)
            return preferences.getString("customer_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("customer_id", it).apply()
            }
        }
    }
}

internal fun KlipyResponse.toRemoteMediaPage(type: RemoteMediaType, query: String = ""): RemoteMediaPage {
    if (!result || data.currentPage < 1) throw IOException("Risposta KLIPY non valida")
    val itemType = when (type) {
        RemoteMediaType.Gif -> "gif"
        RemoteMediaType.Sticker -> "sticker"
        RemoteMediaType.Meme -> "static_meme"
    }
    // Reject an unsupported response instead of silently filtering or reordering its results.
    val items = data.data.map { item ->
        if (item.type != itemType) {
            throw IOException("Contenuto KLIPY non supportato")
        }
        fun rendition(vararg sizes: String, formats: List<String>): KlipyFile? = sizes.firstNotNullOfOrNull { size ->
            formats.firstNotNullOfOrNull { format -> item.file[size]?.get(format)?.takeIf { it.valid() } }
        }
        val sizes = if (type == RemoteMediaType.Gif) arrayOf("sm", "xs", "md", "hd") else arrayOf("hd", "md", "sm")
        val original = rendition(*sizes, formats = if (type == RemoteMediaType.Meme) listOf("png", "jpg", "webp") else listOf("gif"))
            ?: throw IOException("Formato KLIPY mancante")
        val preview = rendition("sm", "xs", "md", "hd", formats = if (type == RemoteMediaType.Meme) listOf("webp", "png", "jpg") else listOf("webp", "gif")) ?: original
        val fallback = rendition("sm", "xs", "md", "hd", formats = if (type == RemoteMediaType.Meme) listOf("png", "jpg", "webp") else listOf("gif")) ?: original
        if (item.slug.isBlank()) throw IOException("Identificativo KLIPY mancante")
        RemoteMedia(id = item.slug, previewUrl = preview.url, mediaUrl = original.url,
            width = original.width, height = original.height, title = item.title, type = type,
            previewFallbackUrl = fallback.url.takeIf { it != preview.url },
            loadingPreviewUrl = rendition("xs", "sm", formats = listOf("jpg", "png"))?.url,
            provider = "klipy", searchQuery = query)
    }
    // nextOffset is an opaque page token for KLIPY.
    return RemoteMediaPage(items, (data.currentPage + 1).takeIf { data.hasNext })
}

private fun KlipyFile.valid(): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.port == 443 &&
        parsed.host in setOf("static.klipy.com", "static1.klipy.com", "static2.klipy.com") &&
        width in 1..8192 && height in 1..8192
}
