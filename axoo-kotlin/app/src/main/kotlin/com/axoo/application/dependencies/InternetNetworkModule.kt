/*
 * Questo file configura le librerie necessarie per comunicare con Internet (Retrofit e OkHttp).
 * Vengono create come "Singleton" in modo da esistere una sola volta in tutta l'applicazione,
 * risparmiando molta memoria e migliorando la velocità della connessione di rete.
 */
package com.axoo.application.dependencies

import com.axoo.BuildConfig
import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.axoo.core.push.PushApi
import com.axoo.features.auth.data.AuthApi
import com.axoo.features.events.data.EventsApi
import com.axoo.features.moderation.data.ModerationApi
import com.axoo.features.moderation.data.ReportsApi
import com.axoo.features.notifications.data.NotificationsApi
import com.axoo.features.posts.data.MediaApi
import com.axoo.features.posts.data.PostsApi
import com.axoo.features.profile.data.ProfileApi
import com.axoo.core.network.AuthInterceptor
import com.axoo.core.session.SupabaseAuthApi
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "axoo_prefs",
    produceMigrations = { listOf(RemoveCachedProfileMigration) },
)

// Remove the profile saved by older versions before any preferences are read.
internal object RemoveCachedProfileMigration : DataMigration<Preferences> {
    private val key = stringPreferencesKey("cached_profile")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.contains(key)

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { remove(key) }

    override suspend fun cleanUp() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
object InternetNetworkModule {

    @Provides
    @Singleton
    fun provideKlipyApi(moshi: Moshi): com.axoo.features.posts.data.KlipyApi =
        com.axoo.features.posts.data.KlipyApi.create(moshi)

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseInstallations(): FirebaseInstallations = FirebaseInstallations.getInstance()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        // Older versions could cache authenticated API responses here.
        context.cacheDir.resolve("http_cache").deleteRecursively()
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideEventsApi(retrofit: Retrofit): EventsApi = retrofit.create(EventsApi::class.java)

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)

    @Provides
    @Singleton
    fun provideModerationApi(retrofit: Retrofit): ModerationApi = retrofit.create(ModerationApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun providePostsApi(retrofit: Retrofit): PostsApi = retrofit.create(PostsApi::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun providePushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)

    @Provides
    @Singleton
    fun provideReportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton
    fun provideSupabaseAuthApi(moshi: Moshi): SupabaseAuthApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "auth/v1/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseAuthApi::class.java)

}
