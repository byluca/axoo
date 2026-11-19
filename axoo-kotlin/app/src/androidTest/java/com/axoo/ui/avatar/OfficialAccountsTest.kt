package com.axoo.ui.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.features.profile.presentation.components.ProfileHeaderView
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialAccountsTest {
    @Test fun officialPhotosAndBadgesRenderWithoutChangingTheSignedInAccount() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (name in listOf("axoo", "aup")) {
            val config = mapOf<String, Any>("official_account" to name)
            val url = buildAvatarUrl(name, config)
            assertTrue(url.startsWith("https://cyrfznxopjeeidcmfgze.supabase.co/"))
            assertNotNull("The supplied avatar must decode", CoilBitmapLoader.load(context, url, context.dp(132)))
        }
        assertFalse(isVerifiedAvatar(mapOf("official_account" to "other")))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var root: LinearLayout
            val headers = mutableListOf<ProfileHeaderView>()
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(NativeColors.background)
                    setPadding(dp(20), dp(12), dp(20), dp(20))
                }
                for ((name, campus) in listOf("axoo" to "UniBa", "aup" to "PoliBa")) {
                    val config = mapOf<String, Any>("official_account" to name)
                    val header = ProfileHeaderView(activity).apply { bind(name, campus, buildAvatarUrl(name, config), true) }
                    headers += header
                    root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = root.dp(16) })
                }
                val post = Post(id = "official-preview", userId = "aup", nickname = "aup", universityId = "poliba",
                    universityName = "PoliBa", body = "Account ufficiale dei rappresentanti.",
                    createdAt = "2026-10-23T19:00:00Z", avatarConfig = mapOf("official_account" to "aup"))
                root.addView(NativePostView(activity).apply { bind(post, {}, {}, {}, {}, { _, _ -> }) }, LinearLayout.LayoutParams(-1, -2))
                activity.setContentView(ScrollView(activity).apply { addView(root) })
            }
            SystemClock.sleep(700)
            scenario.onActivity {
                for (header in headers) {
                    val name = header.getChildAt(1) as TextView
                    assertNotNull("Verified name must show the supplied badge", name.compoundDrawablesRelative[2])
                }
            }
            scenario.onActivity {
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                File(context.cacheDir, "official-accounts.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }
}
