package com.axoo.features.events.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EventsApi {
    @GET("events/{id}")
    suspend fun getEvent(@Path("id") id: String): CampusEvent

    @PATCH("events/{id}")
    suspend fun updateEvent(@Path("id") id: String, @Body input: EventInput): CampusEvent

    @GET("events")
    suspend fun getEvents(@Query("cursor") cursor: String? = null): EventsPage

    @POST("events")
    suspend fun createEvent(@Body input: EventInput): CampusEvent

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: String)
}
