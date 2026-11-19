package com.axoo.features.posts.data

import com.axoo.features.posts.data.dto.KlipyResponse
import com.axoo.features.posts.data.dto.KlipyShareRequest
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface KlipyApi {
    @GET("api/v1/{key}/{collection}/{endpoint}")
    suspend fun list(
        @Path("key") key: String,
        @Path("collection") collection: String,
        @Path("endpoint") endpoint: String,
        @QueryMap query: Map<String, String>,
    ): KlipyResponse

    @POST("api/v1/{key}/{collection}/share/{slug}")
    suspend fun share(
        @Path("key") key: String,
        @Path("collection") collection: String,
        @Path("slug") slug: String,
        @Body body: KlipyShareRequest,
    )

    companion object {
        // A separate client keeps Axoo credentials and API-key URLs out of HTTP logs.
        fun create(moshi: Moshi): KlipyApi = Retrofit.Builder()
            .baseUrl("https://api.klipy.com/")
            .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build().create(KlipyApi::class.java)
    }
}
