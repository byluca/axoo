package com.axoo.features.profile.presentation.bio

import com.axoo.features.profile.data.ProfileApi
import com.axoo.features.posts.data.PostsApi
import com.axoo.features.moderation.data.ReportsApi
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.core.session.SupabaseAuthApi
import com.axoo.core.session.SupabaseSessionStore
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.data.dto.UpdateBioRequest
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.ProfileUiState
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.features.profile.presentation.components.NativeProfileRootView
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BioEditorPickerTest {
    @Test fun pickerInsertsCustomEmojiAndSavePreservesItInTheSeparateBioPill() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        lateinit var host: BioEditorHost
        lateinit var model: ProfileViewModel
        var current = User("bio-test", "student", "campus", universityName = "Università", createdAt = "now", bio = "Musica, caffè e ")
        var closed = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    EmojiData.configure(activity)
                    val payload = Base64.encodeToString("{\"sub\":\"bio-test\"}".toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    val preferences = MutableStateFlow<Preferences>(preferencesOf(stringPreferencesKey("supabase_access_token") to "header.$payload.signature"))
                    val dataStore = object : DataStore<Preferences> {
                        override val data = preferences
                        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(preferences.value).also { preferences.value = it }
                    }
                    val auth = Proxy.newProxyInstance(SupabaseAuthApi::class.java.classLoader, arrayOf(SupabaseAuthApi::class.java)) { _, _, _ -> error("No network in this test") } as SupabaseAuthApi
                    val sessions = SupabaseSessionStore(dataStore, auth)
                    val api = Proxy.newProxyInstance(ProfileApi::class.java.classLoader, arrayOf(ProfileApi::class.java)) { _, method, args ->
                        when (method.name) {
                            "getMe" -> current
                            "updateBio" -> (args!![0] as UpdateBioRequest).let { request ->
                                current = current.copy(bio = request.bio, bioEntities = request.bioEntities)
                                current
                            }
                            else -> error("Unexpected API call: ${method.name}")
                        }
                    } as ProfileApi
                    val postsApi = Proxy.newProxyInstance(PostsApi::class.java.classLoader, arrayOf(PostsApi::class.java)) { _, method, _ ->
                        error("Unexpected posts API call: ${method.name}")
                    } as PostsApi
                    val reportsApi = Proxy.newProxyInstance(ReportsApi::class.java.classLoader, arrayOf(ReportsApi::class.java)) { _, method, _ ->
                        error("Unexpected reports API call: ${method.name}")
                    } as ReportsApi
                    model = ProfileViewModel(ProfileRepository(reportsApi, api, PostRepository(reportsApi, postsApi, com.axoo.features.posts.data.KlipyRepository(
                        com.axoo.features.posts.data.KlipyApi.create(com.squareup.moshi.Moshi.Builder().build()), activity)), sessions))
                    store.put("bio", model)
                    host = BioEditorHost(activity, model, activity) { closed = true }
                    activity.setContentView(host)
                    host.onScreenFullyVisible()
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(600)
                screenshot("bio-editor.png")
                scenario.onActivity {
                    descendants(host).first { it.contentDescription == "Apri emoji" }.performClick()
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(600)
                scenario.onActivity {
                    val grid = descendants(host).filterIsInstance<RecyclerView>().first { it.isShown }
                    assertTrue(grid.height > 0)
                    assertFalse(descendants(host).filterIsInstance<android.widget.TextView>().any { it.isShown && it.text.toString() in listOf("GIF", "Sticker") })
                    val cell = checkNotNull(grid.findViewHolderForAdapterPosition(1)) { "First emoji cell was not laid out" }
                    cell.itemView.performClick()
                    assertEquals(1, model.bio.value.entities.size)
                    assertTrue(model.bio.value.canSave)
                }
                instrumentation.waitForIdleSync()
                screenshot("bio-picker.png")
                scenario.onActivity {
                    descendants(host).first { it.contentDescription == "Apri tastiera" }.performClick()
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(600)
                scenario.onActivity {
                    assertFalse(descendants(host).filterIsInstance<RecyclerView>().any { it.isShown })
                    assertEquals(1, model.bio.value.entities.size)
                }
                scenario.onActivity { model.saveBio() }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(closed)
                    assertEquals(1, model.me.value.getOrNull()!!.bioEntities.size)
                    host.onScreenDestroyed()
                    val root = NativeProfileRootView(it)
                    root.render(ProfileUiState(user = model.me.value.getOrNull()))
                    root.setBioActive(true)
                    it.setContentView(root)
                }
                instrumentation.waitForIdleSync()
                screenshot("bio-profile.png")
            } finally {
                scenario.onActivity { host.onScreenDestroyed(); store.clear() }
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SystemClock.sleep(200)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap { index ->
        val view = group.getChildAt(index)
        listOf(view) + if (view is ViewGroup) descendants(view) else emptyList()
    }
}
