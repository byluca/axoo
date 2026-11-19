package com.axoo.features.posts.data

import com.axoo.features.posts.domain.ImageMeta
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MediaApi {
    @GET("media/stickers/mine")
    suspend fun getPersonalStickers(): PersonalStickerPage

    @Multipart
    @POST("media/stickers")
    suspend fun createSticker(@Part sticker: MultipartBody.Part): ImageMeta

    @POST("media/stickers/save")
    suspend fun saveSticker(@Body sticker: ImageMeta): ImageMeta

    @Multipart
    @POST("media/photos")
    suspend fun uploadPostPhoto(@Part photo: MultipartBody.Part): ImageMeta
}
