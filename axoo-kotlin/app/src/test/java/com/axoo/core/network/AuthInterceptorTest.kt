package com.axoo.core.network

import com.axoo.core.session.SupabaseSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class AuthInterceptorTest {
    private val store = mockk<SupabaseSessionStore>()
    private val chain = mockk<Interceptor.Chain>()
    private val response = mockk<Response>()

    @Test fun `logout token removal uses only the old explicit credential`() {
        val request = Request.Builder().url("https://example.test/me/fcm-token")
            .header("Authorization", "Bearer old-account").build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response
        AuthInterceptor(store).intercept(chain)
        coVerify(exactly = 0) { store.accessToken() }
        verify { chain.proceed(match { it.headers.values("Authorization") == listOf("Bearer old-account") }) }
    }

    @Test fun `normal request still receives current session credential`() {
        every { chain.request() } returns Request.Builder().url("https://example.test/me").build()
        every { chain.proceed(any()) } returns response
        coEvery { store.accessToken() } returns "current-account"
        AuthInterceptor(store).intercept(chain)
        verify { chain.proceed(match { it.header("Authorization") == "Bearer current-account" }) }
    }
}
