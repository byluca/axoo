package com.axoo.features.posts.domain

/** Original AUP WebP files, hosted with the app's own public media. */
object AupStickers {
    private const val BASE_URL = "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/"
    val all = (1..17).map { number ->
        val url = "$BASE_URL$number.webp"
        RemoteMedia("aup-$number", url, url, 512, 512, "Sticker AUP $number", RemoteMediaType.Sticker,
            previewFallbackUrl = if (number == 12) "${BASE_URL}12.gif" else null,
            provider = "aup", mimeType = "image/webp")
    }
}
