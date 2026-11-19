package com.axoo.features.profile.presentation.components

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.ProfileUiState
import com.axoo.ui.theme.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileMyPostsTest {
    @Test fun myPostsUsesFullWidthWithoutLeaderboardAndDispatchesTap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            var opened = false
            renderProfileContent(content, ProfileUiState(user = User(id = "me", nickname = "Me", universityId = "uniba", createdAt = "now")), {}, {}, { opened = true })
            content.measure(View.MeasureSpec.makeMeasureSpec(context.dp(390), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)
            val card = content.getChildAt(0)
            val bio = content.getChildAt(1) as ProfileBioView
            assertEquals(View.GONE, bio.getChildAt(0).visibility)
            assertEquals("Bio", (bio.getChildAt(1) as TextView).text.toString())
            val row = content.getChildAt(2) as LinearLayout
            assertEquals(1, row.childCount)
            val pill = row.getChildAt(0) as LinearLayout
            assertEquals("I miei post", (pill.getChildAt(1) as TextView).text.toString())
            assertEquals(card.left, row.left)
            assertTrue(bio.top > card.bottom)
            assertEquals(card.left, bio.left)
            assertEquals(card.width, bio.width)
            assertTrue(row.top > bio.bottom)
            assertEquals(row.width, pill.width)
            pill.performClick()
            assertTrue(opened)
        }
    }
}
