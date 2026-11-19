package com.axoo.features.posts.presentation.postdetail.components

import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import com.axoo.ui.theme.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostDetailSendScrollTest {
    @Test fun firstRootCommentScrollsToTheEndWithAnEmptyOrPopulatedList() {
        for (initialCount in listOf(0, 20)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var content: PostDetailContentView
                lateinit var list: RecyclerView
                val comments = (0 until initialCount).map { index -> CommentUiModel(Comment(
                    id = "root-$index", postId = "post", userId = "author", nickname = "Autore",
                    body = "Commento $index", createdAt = "2026-10-07T12:00:00Z")) }
                scenario.onActivity { activity ->
                    content = PostDetailContentView(activity)
                    activity.findViewById<ViewGroup>(android.R.id.content).addView(content,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(280)))
                    list = content.getChildAt(0) as RecyclerView
                    content.render(null, comments, false, false)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    if (initialCount > 0) assertTrue(list.canScrollVertically(1))
                    val reply = CommentUiModel(Comment(id = "reply", postId = "post", userId = "me",
                        parentCommentId = null, body = "Nuovo commento", createdAt = "2026-10-07T13:00:00Z"))
                    content.render(null, comments + reply, false, false)
                    content.scrollToBottom()
                }
                val deadline = SystemClock.uptimeMillis() + 5_000
                var atBottom = false
                while (!atBottom && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity {
                        val manager = list.layoutManager as LinearLayoutManager
                        atBottom = manager.findLastCompletelyVisibleItemPosition() == list.adapter!!.itemCount - 1 &&
                            !list.canScrollVertically(1)
                    }
                    if (!atBottom) SystemClock.sleep(25)
                }
                scenario.onActivity {
                    assertTrue("Sending a new root comment must reach the end", atBottom)
                    assertFalse(list.canScrollVertically(1))
                    content.release()
                    (content.parent as ViewGroup).removeView(content)
                }
            }
        }
    }
}
