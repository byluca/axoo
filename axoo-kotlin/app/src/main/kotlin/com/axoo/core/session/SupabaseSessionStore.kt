package com.axoo.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.axoo.BuildConfig
import com.axoo.core.session.SupabaseAuthApi
import com.axoo.core.session.SupabaseRefreshTokenRequest
import com.axoo.core.session.SupabaseSessionResponse
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64
import retrofit2.HttpException

private val ACCESS_TOKEN_KEY = stringPreferencesKey("supabase_access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("supabase_refresh_token")
private val EXPIRES_AT_KEY = longPreferencesKey("supabase_expires_at")

@Singleton
class SupabaseSessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val supabaseAuthApi: SupabaseAuthApi,
) {
    private val sessionLock = Mutex()
    private val refreshLock = Mutex()
    private data class Version(val generation: Long = 0, val blocked: Boolean = false)
    private val version = AtomicReference(Version())
    val generation: Long get() = version.get().generation

    // Synchronous: requests must stop using credentials even if disk cleanup fails.
    fun invalidate() {
        version.updateAndGet { Version(it.generation + 1, blocked = true) }
    }

    suspend fun save(session: SupabaseSessionResponse, expectedGeneration: Long = generation): Long = sessionLock.withLock {
        val before = version.get()
        if (before.generation != expectedGeneration) throw CancellationException("Sessione superata")
        saveUnlocked(session)
        val after = Version(before.generation + 1)
        if (!version.compareAndSet(before, after)) throw CancellationException("Sessione terminata")
        after.generation
    }

    private suspend fun saveUnlocked(session: SupabaseSessionResponse) {
        dataStore.edit {
            it[ACCESS_TOKEN_KEY] = session.accessToken
            it[REFRESH_TOKEN_KEY] = session.refreshToken
            // Refresh a little early so normal API calls never race token expiration.
            it[EXPIRES_AT_KEY] = System.currentTimeMillis() + (session.expiresIn - 30).coerceAtLeast(0) * 1_000
        }
    }

    suspend fun accessToken(): String? {
        val before = version.get()
        if (before.blocked) return null
        return refreshLock.withLock { accessTokenUnlocked(before) }
    }

    private suspend fun accessTokenUnlocked(before: Version): String? {
        if (version.get() != before) return null
        val preferences = dataStore.data.first()
        if (version.get() != before) return null
        val accessToken = preferences[ACCESS_TOKEN_KEY] ?: return null
        if ((preferences[EXPIRES_AT_KEY] ?: 0) > System.currentTimeMillis()) return accessToken

        val refreshToken = preferences[REFRESH_TOKEN_KEY] ?: return null
        return try {
            val refreshed = supabaseAuthApi.refreshSession(BuildConfig.SUPABASE_PUBLISHABLE_KEY, request = SupabaseRefreshTokenRequest(refreshToken))
            // Network refresh never holds sessionLock: logout need not wait for it.
            sessionLock.withLock {
                if (version.get() != before) return null
                saveUnlocked(refreshed)
                if (version.get() == before) refreshed.accessToken else null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (version.get() != before) return null
            if (error is HttpException && error.code() in setOf(400, 401)) {
                clearIfGeneration(before.generation)
                null
            } else {
                // OkHttp interceptors must propagate IOExceptions, not Retrofit exceptions.
                throw IOException("Rinnovo della sessione temporaneamente non disponibile", error)
            }
        }
    }

    // Session existence must not depend on a network request succeeding.
    suspend fun hasSession(): Boolean {
        val before = version.get()
        if (before.blocked) return false
        val preferences = dataStore.data.first()
        return version.get() == before &&
            preferences[ACCESS_TOKEN_KEY] != null && preferences[REFRESH_TOKEN_KEY] != null
    }

    suspend fun currentUserId(): String? {
        val before = version.get()
        if (before.blocked) return null
        val token = dataStore.data.first()[ACCESS_TOKEN_KEY] ?: return null
        if (version.get() != before) return null
        return runCatching {
            val payload = token.split('.')[1].decodeBase64()?.utf8() ?: return null
            Moshi.Builder().build().adapter(Map::class.java).fromJson(payload)?.get("sub") as? String
        }.getOrNull()
    }

    suspend fun clear(): String? {
        invalidate()
        return sessionLock.withLock {
            val oldAccessToken = dataStore.data.first()[ACCESS_TOKEN_KEY]
            clearUnlocked()
            oldAccessToken
        }
    }

    // A late 401 for account A must never delete account B's credentials.
    suspend fun clearIfGeneration(expectedGeneration: Long): Boolean = sessionLock.withLock {
        val before = version.get()
        if (before.generation != expectedGeneration ||
            !version.compareAndSet(before, Version(before.generation + 1, blocked = true))
        ) return false
        clearUnlocked()
        true
    }

    private suspend fun clearUnlocked() {
        dataStore.edit {
            it.remove(ACCESS_TOKEN_KEY)
            it.remove(REFRESH_TOKEN_KEY)
            it.remove(EXPIRES_AT_KEY)
        }
    }
}
