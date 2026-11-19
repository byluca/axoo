package com.axoo.core.push

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.axoo.core.session.SupabaseSessionStore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.squareup.moshi.JsonClass
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

@JsonClass(generateAdapter = true)
data class PushRegistrationRequest(val registration: String)

@Singleton
class PushRegistration @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val api: PushApi,
    private val session: SupabaseSessionStore,
    private val messaging: Provider<FirebaseMessaging>,
    private val installations: Provider<FirebaseInstallations>,
    private val notifications: PushNotifications,
) {
    private val lock = Mutex()
    private val cleanupScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    @Volatile private var active = false

    suspend fun activate() {
        active = true
        lock.withLock { sync(null) }
    }

    // Stop callbacks immediately, before AuthRepository blocks access to the old session.
    fun stop() {
        active = false
        notifications.clear()
    }

    suspend fun synchronize(currentRegistration: String? = null) {
        // register() also triggers onRegistered; coalesce that callback with the current sync.
        if (!lock.tryLock()) return
        try { sync(currentRegistration) }
        finally { lock.unlock() }
    }

    private suspend fun sync(currentRegistration: String?) {
        if (!active || !notifications.isEnabled()) return
        val generation = session.generation
        try {
            withTimeoutOrNull(8_000) {
                session.currentUserId() ?: return@withTimeoutOrNull
                val token = session.accessToken() ?: return@withTimeoutOrNull
                val client = messaging.get()
                val resetPending = dataStore.data.first()[RESET_PENDING] == true
                if (resetPending) {
                    client.unregister().await()
                    dataStore.edit { it.remove(RESET_PENDING) }
                }
                val registration = if (currentRegistration != null && !resetPending) currentRegistration else {
                    client.register().await()
                    installations.get().id.await()
                }
                if (!active || generation != session.generation) return@withTimeoutOrNull
                var installationID = ""
                dataStore.edit {
                    installationID = it[INSTALLATION_ID] ?: UUID.randomUUID().toString()
                    it[INSTALLATION_ID] = installationID
                    // A timed-out PUT may still commit; logout must know the attempted binding.
                    it[REGISTRATION] = registration
                }
                api.registerPushInstallation(installationID, PushRegistrationRequest(registration), "Bearer $token")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("PushRegistration", "Registrazione push rimandata al prossimo accesso", error)
        }
    }

    suspend fun clearSignedOut() {
        if (session.hasSession()) return
        val saved = dataStore.data.first()
        if (saved[REGISTRATION] != null || saved[RESET_PENDING] == true) unregister(null)
    }

    fun unregister(accessToken: String?) {
        // Queue behind pending uploads before returning; the next activation uses the same lock.
        cleanupScope.launch(start = CoroutineStart.UNDISPATCHED) {
            removeInstallation(accessToken)
        }
    }

    private suspend fun removeInstallation(accessToken: String?) = lock.withLock {
        // An expiry callback queued before a new login must leave the new session alone.
        if (accessToken == null && session.hasSession()) return@withLock
        try {
            val saved = dataStore.data.first()
            if (accessToken == null && saved[REGISTRATION] == null && saved[RESET_PENDING] != true) return@withLock
            dataStore.edit { it[RESET_PENDING] = true }
            val client = messaging.get()
            client.isAutoInitEnabled = false
            val installationID = saved[INSTALLATION_ID]
            val registration = saved[REGISTRATION]
            if (accessToken != null && installationID != null && registration != null) {
                try {
                    withTimeout(3_000) {
                        api.removePushInstallation(installationID, PushRegistrationRequest(registration), "Bearer $accessToken")
                    }
                } catch (_: Exception) { /* SDK invalidation is still needed if the API is offline. */ }
            }
            client.unregister().await()
            dataStore.edit {
                it.remove(REGISTRATION)
                it.remove(RESET_PENDING)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Log.w("PushRegistration", "Pulizia Firebase rimandata al prossimo accesso")
        }
    }

    suspend fun isCurrentRecipient(userID: String): Boolean = active && session.currentUserId() == userID

    // Persisted per installation, so refusing the prompt does not repeat it on every login.
    suspend fun shouldAskPermission(): Boolean = dataStore.data.first()[PERMISSION_ASKED] != true
    suspend fun markPermissionAsked() { dataStore.edit { it[PERMISSION_ASKED] = true } }

    private companion object {
        val INSTALLATION_ID = stringPreferencesKey("push_installation_id")
        val REGISTRATION = stringPreferencesKey("push_registration")
        val RESET_PENDING = booleanPreferencesKey("push_reset_pending")
        val PERMISSION_ASKED = booleanPreferencesKey("push_permission_asked")
    }
}
