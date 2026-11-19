package com.axoo.features.posts.data

import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.RemoteMediaType
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PersonalStickerRepositoryTest {
    @Test fun `save sends original metadata and library reuses the account copy as a sticker`() = runBlocking {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        val id = "3359c318-5414-4ba8-a15a-5fc0c905d817"
        val original = ImageMeta("https://example.test/first/$id.png", width = 512, height = 512, type = "sticker",
            provider = "axoo", providerId = id, mimeType = "image/png")
        val copy = """{"url":"https://example.test/me/$id.png","width":512,"height":512,"type":"sticker","provider":"axoo","provider_id":"$id","mime_type":"image/png"}"""
        val api = Retrofit.Builder().baseUrl("https://example.test/")
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
                val result = if (request.method == "GET") """{"items":[$copy]}""" else copy
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(result.toResponseBody("application/json".toMediaType())).build()
            }.build())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build().create(MediaApi::class.java)
        val repository = PersonalStickerRepository(api)
        val saved = repository.save(original)
        assertEquals("https://example.test/me/$id.png", saved.url)
        assertEquals("/media/stickers/save", requests[0].url.encodedPath)
        val sent = Moshi.Builder().build().adapter(ImageMeta::class.java).fromJson(bodies[0])
        assertEquals(original, sent)
        val library = repository.list().single()
        assertEquals("/media/stickers/mine", requests[1].url.encodedPath)
        assertEquals(RemoteMediaType.GiphySticker, library.type)
        assertEquals(id, library.id)
        assertEquals(saved.url, library.mediaUrl)
        assertEquals("image/png", library.mimeType)
    }
}
