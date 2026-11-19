package com.axoo.core.network

import com.axoo.core.session.SupabaseSessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Il compito di questa classe è uno solo: prendere il token dalla nostra "cassaforte"
 * e appiccicarlo automaticamente a ogni singola richiesta di rete in partenza verso il server.
 *
 * (Usiamo runBlocking per mettere in pausa la richiesta, poiché OkHttp lo richiede in modo sincrono).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SupabaseSessionStore, //session store e' la cassaforte dove salviamo i dati dell'utente come il token
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Logout cleanup carries the old token explicitly, never the next account's.
        if (chain.request().header("Authorization") != null) return chain.proceed(chain.request())
        // runBlocking mette in pausa l'invio della richiesta di rete finché
        // non abbiamo finito di leggere il token asincronamente dalla "cassaforte".
        val token = runBlocking { sessionStore.accessToken() }

        // 1. Prende la richiesta originale in partenza (chain.request())
        // 2. Ne fa una copia modificabile (.newBuilder())
        // 3. Se c'è un token, appiccica il "passaporto" (header Authorization)
        // 4. Crea la richiesta definitiva (.build())
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()

        // Dice al sistema: "ok, procedi (proceed) spedendo questa NUOVA richiesta modificata"
        return chain.proceed(request)
    }
}
