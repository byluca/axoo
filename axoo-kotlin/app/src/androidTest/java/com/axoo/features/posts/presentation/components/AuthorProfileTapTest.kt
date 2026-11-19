package com.axoo.features.posts.presentation.components

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import com.axoo.ui.theme.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorProfileTapTest {
    @Test fun postAvatarAndNicknameOpenAuthorWhileBodyStillOpensPost() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val post = Post("post", "author", universityId = "campus", nickname = "student", body = "Testo", createdAt = "2026-10-23T00:00:00Z")
        for (presentation in NativePostPresentation.entries) {
            val view = NativePostView(context, presentation)
            val actions = mutableListOf<String>()
            view.bind(post, onOpen = { actions += "post" }, onUpvote = { actions += "vote" },
                onDownvote = {}, onComment = {}, onMenu = { _, _ -> actions += "menu" },
                onAuthor = { actions += it })
            measure(view)
            tap(view, "avatarRect")
            tap(view, "authorRect")
            tap(view, "upvoteRect")
            tap(view, "menuRect")
            val body = view.javaClass.getDeclaredField("bodyTop").apply { isAccessible = true }.getFloat(view)
            tap(view, view.width / 2f, body + context.dp(4))
            assertEquals(listOf("author", "author", "vote", "menu", "post"), actions)
            view.recycle()
        }
    }

    @Test fun commentAuthorTapsRemainSeparateFromReplyAndDeletedComments() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = NativeCommentView(context)
        val comment = Comment("comment", postId = "post", userId = "author", nickname = "student", body = "Ciao", createdAt = "2026-10-23T00:00:00Z")
        val actions = mutableListOf<String>()
        view.bind(CommentUiModel(comment), onAuthor = { actions += it }, onReply = { actions += "reply" })
        measure(view)
        tap(view, "avatarRect")
        tap(view, "authorRect")
        tap(view, "replyRect")
        view.bind(CommentUiModel(comment.copy(isDeleted = true)), onAuthor = { actions += it })
        measure(view)
        tap(view, "avatarRect")
        assertEquals(listOf("author", "author", "reply"), actions)
        view.recycle()
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(view.context.dp(390), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun tap(view: View, field: String) {
        val rect = view.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(view) as RectF
        tap(view, rect.centerX(), rect.centerY())
    }

    private fun tap(view: View, x: Float, y: Float) {
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            view.onTouchEvent(event)
            event.recycle()
        }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
