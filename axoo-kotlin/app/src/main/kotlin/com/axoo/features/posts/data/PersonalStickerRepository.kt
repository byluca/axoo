package com.axoo.features.posts.data

import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.squareup.moshi.JsonClass
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class PersonalStickerPage(val items: List<ImageMeta>)

class PersonalStickerRepository @Inject constructor(private val api: MediaApi) {
    suspend fun list(): List<RemoteMedia> = api.getPersonalStickers().items.map { it.asPersonalSticker() }
    suspend fun save(sticker: ImageMeta): ImageMeta = api.saveSticker(sticker)

    suspend fun create(png: ByteArray): ImageMeta = api.createSticker(
        MultipartBody.Part.createFormData("sticker", "sticker.png", png.toRequestBody("image/png".toMediaType())),
    )
}

internal fun ImageMeta.asPersonalSticker() = RemoteMedia(
    id = requireNotNull(providerId), previewUrl = url, mediaUrl = url,
    width = width, height = height, title = "Sticker personale", type = RemoteMediaType.Sticker,
    provider = "axoo", mimeType = "image/png",
)
