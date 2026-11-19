package com.axoo.core.session

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/** The only OTP authority: Supabase Auth (which uses Resend as its configured SMTP). */
interface SupabaseAuthApi {
    @POST("otp")
    suspend fun sendEmailOtp(
        @Header("apikey") publishableKey: String,
        @Body request: SupabaseOtpRequest,
    )

    @POST("verify")
    suspend fun verifyEmailOtp(
        @Header("apikey") publishableKey: String,
        @Body request: SupabaseVerifyOtpRequest,
    ): SupabaseSessionResponse

    @POST("token")
    suspend fun signInWithPassword(
        @Header("apikey") publishableKey: String,
        @Query("grant_type") grantType: String = "password",
        @Body request: SupabasePasswordRequest,
    ): SupabaseSessionResponse

    @POST("token")
    suspend fun refreshSession(
        @Header("apikey") publishableKey: String,
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: SupabaseRefreshTokenRequest,
    ): SupabaseSessionResponse
}

//scatoloni di dati che viaggiano su internet
@JsonClass(generateAdapter = true)
data class SupabaseOtpRequest(
    val email: String,
    @Json(name = "create_user") val createUser: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
    val email: String,
    val token: String,
    val type: String = "email",
)

@JsonClass(generateAdapter = true)
data class SupabasePasswordRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class SupabaseRefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class SupabaseSessionResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
)
