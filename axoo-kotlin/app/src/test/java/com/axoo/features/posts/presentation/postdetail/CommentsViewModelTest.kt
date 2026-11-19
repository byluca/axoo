package com.axoo.features.posts.presentation.postdetail

import androidx.lifecycle.SavedStateHandle
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.RetryVoteUseCase
import com.axoo.features.posts.domain.PostAccount
import com.axoo.features.posts.domain.PostAuthor
import com.axoo.testing.MainDispatcherRule
import com.axoo.ui.state.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val changes = kotlinx.coroutines.flow.MutableSharedFlow<com.axoo.features.posts.data.PostChange>()
    init { io.mockk.every { postRepository.changes } returns changes }
    private val retryVoteUseCase = mockk<RetryVoteUseCase>(relaxed = true)
    private val postAccount = mockk<PostAccount>()

    @Test
    fun `sent stickers cannot be edited or replace the text draft`() = runTest {
        val sticker = comment("sticker").copy(userId = "current-user", body = "",
            media = com.axoo.features.posts.domain.ImageMeta("https://media.giphy.com/a.gif", width = 200, height = 100,
                type = "sticker", provider = "giphy", providerId = "a"))
        prepareInitialLoad(post(), listOf(sticker))
        val model = createViewModel()
        advanceUntilIdle()
        model.updateReplyDraft("Bozza da conservare", emptyList(), 3, 3)
        assertEquals(false, sticker.canEdit)
        assertEquals(false, model.beginEdit(sticker))
        assertEquals(null, model.state.value.editingComment)
        assertEquals("Bozza da conservare", model.state.value.replyDraft.text)
        model.editComment(sticker.id, "Testo aggiunto", emptyList()) { assertEquals(true, it.isFailure) }
        advanceUntilIdle()
        coVerify(exactly = 0) { postRepository.updateComment(any(), any(), any(), any()) }
        val gif = sticker.copy(media = sticker.media!!.copy(type = "gif"))
        assertEquals(true, gif.canEdit)
        assertEquals(true, model.beginEdit(gif))
    }

    @Test
    fun `prohibited comment is returned to the composer and is not retried`() = runTest {
        prepareInitialLoad(post())
        val rejection = HttpException(Response.error<Any>(400,
            """{"code":"prohibited_words","error":"Il testo contiene parole non consentite. Modificalo e riprova."}""".toResponseBody()))
        coEvery { postRepository.createComment(any(), any(), any(), any(), any()) } throws rejection
        val model = createViewModel()
        advanceUntilIdle()
        model.updateReplyDraft("Testo respinto", emptyList(), 0, 0)
        model.submitReply()
        advanceUntilIdle()
        assertEquals("Testo respinto", model.state.value.replyDraft.text)
        assertEquals("Il testo contiene parole non consentite. Modificalo e riprova.", model.state.value.replyError)
        assertEquals(0, commentsOf(model).size)
        coVerify(exactly = 1) { postRepository.createComment(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `media sends immediately preserves typed draft and reply retries use the same attachment`() = runTest {
        val target = comment("parent")
        prepareInitialLoad(post(), comments = listOf(target))
        val media = com.axoo.features.posts.domain.ImageMeta("https://media.giphy.com/a.gif", width = 200, height = 100,
            type = "sticker", provider = "giphy", providerId = "a")
        val sent = comment("sent").copy(body = "", parentCommentId = target.id, media = media)
        var attempts = 0
        val clientIds = mutableListOf<String>()
        coEvery { postRepository.createComment("post-1", any(), "", emptyList(), target.id, media) } answers {
            clientIds += secondArg<String>()
            if (attempts++ == 0) throw IOException("offline")
            sent
        }
        val model = createViewModel()
        advanceUntilIdle()
        model.updateReplyDraft("Bozza da conservare", emptyList(), 3, 3)
        model.beginReply(target)
        model.sendMedia(media)
        assertEquals("Bozza da conservare", model.state.value.replyDraft.text)
        assertEquals(3, model.state.value.replyDraft.selectionStart)
        assertEquals(null, model.state.value.replyDraft.replyToCommentId)
        assertEquals(media, commentsOf(model).last().comment.media)
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(1, clientIds.distinct().size)
        assertEquals(media, commentsOf(model).last().comment.media)
    }

    @Test
    fun `initial load exposes post comments and current user`() = runTest {
        val post = post()
        val comment = comment("comment-hot")
        prepareInitialLoad(post, comments = listOf(comment))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(post, (state.post as UiState.Success).data)
        assertEquals(listOf(comment), (state.comments as UiState.Success).data.map { it.comment })
        assertEquals("current-user", state.currentUserId)
    }

    @Test
    fun `notification refresh loads new comments without a pull indicator or duplicate post requests`() = runTest {
        val initialPost = post()
        val existing = comment("existing")
        prepareInitialLoad(initialPost, listOf(existing))
        val model = createViewModel()
        advanceUntilIdle()
        val response = CompletableDeferred<List<Comment>>()
        coEvery { postRepository.getComments("post-1") } coAnswers { response.await() }

        model.refreshDetail(showIndicator = false)
        model.refreshDetail(showIndicator = false)
        model.refreshPostIfLoaded()
        runCurrent()

        assertEquals(true, model.state.value.isRefreshing)
        assertEquals(false, model.state.value.isPullRefreshing)
        assertEquals(initialPost, (model.state.value.post as UiState.Success).data)
        assertEquals(listOf(existing), commentsOf(model).map { it.comment })
        coVerify(exactly = 2) { postRepository.getPost("post-1") }
        coVerify(exactly = 2) { postRepository.getComments("post-1") }

        val notified = comment("from-notification")
        response.complete(listOf(existing, notified))
        advanceUntilIdle()

        assertEquals(listOf(existing, notified), commentsOf(model).map { it.comment })
        assertEquals(false, model.state.value.isRefreshing)
        assertEquals(false, model.state.value.isPullRefreshing)
    }

    @Test
    fun `manual refresh shows the pull indicator until post and comments finish loading`() = runTest {
        prepareInitialLoad(post())
        val model = createViewModel()
        advanceUntilIdle()
        val response = CompletableDeferred<List<Comment>>()
        coEvery { postRepository.getComments("post-1") } coAnswers { response.await() }

        model.refreshDetail()
        runCurrent()

        assertEquals(true, model.state.value.isRefreshing)
        assertEquals(true, model.state.value.isPullRefreshing)

        response.complete(listOf(comment("new-comment")))
        advanceUntilIdle()

        assertEquals(false, model.state.value.isRefreshing)
        assertEquals(false, model.state.value.isPullRefreshing)
        assertEquals("new-comment", commentsOf(model).single().id)
    }

    @Test
    fun `pulling during notification refresh shows the indicator without starting another request`() = runTest {
        prepareInitialLoad(post())
        val model = createViewModel()
        advanceUntilIdle()
        val response = CompletableDeferred<List<Comment>>()
        coEvery { postRepository.getComments("post-1") } coAnswers { response.await() }

        model.refreshDetail(showIndicator = false)
        runCurrent()
        model.refreshDetail()
        runCurrent()

        assertEquals(true, model.state.value.isPullRefreshing)
        coVerify(exactly = 2) { postRepository.getPost("post-1") }
        coVerify(exactly = 2) { postRepository.getComments("post-1") }

        response.complete(emptyList())
        advanceUntilIdle()
        assertEquals(false, model.state.value.isPullRefreshing)
    }

    @Test
    fun `failed notification refresh retains loaded content and clears refresh state`() = runTest {
        val initialPost = post()
        val existing = comment("existing")
        prepareInitialLoad(initialPost, listOf(existing))
        val model = createViewModel()
        advanceUntilIdle()
        coEvery { postRepository.getPost("post-1") } throws IOException("offline")
        coEvery { postRepository.getComments("post-1") } throws IOException("offline")

        model.refreshDetail(showIndicator = false)
        advanceUntilIdle()

        assertEquals(initialPost, (model.state.value.post as UiState.Success).data)
        assertEquals(listOf(existing), commentsOf(model).map { it.comment })
        assertEquals(false, model.state.value.isRefreshing)
        assertEquals(false, model.state.value.isPullRefreshing)
    }


    @Test
    fun `failed delete restores optimistically removed comment`() = runTest {
        val comment = comment("comment-1")
        prepareInitialLoad(post(), comments = listOf(comment))
        coEvery { postRepository.deleteComment("post-1", "comment-1") } throws IOException("offline")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteComment("comment-1")

        assertEquals(emptyList<CommentUiModel>(), commentsOf(viewModel))
        advanceUntilIdle()
        assertEquals(listOf(comment), commentsOf(viewModel).map { it.comment })
    }

    @Test
    fun `reply sends the target comment id`() = runTest {
        val created = comment("comment-created").copy(parentCommentId = "comment-target")
        prepareInitialLoad(post())
        coEvery {
            postRepository.createComment(
                postId = "post-1",
                clientId = any(),
                body = "Risposta senza tag",
                entities = emptyList(),
                replyToCommentId = "comment-target",
            )
        } returns created
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createComment(
            body = "Risposta senza tag",
            replyToCommentId = "comment-target",
        )
        assertEquals("comment-target", commentsOf(viewModel).single().comment.parentCommentId)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            postRepository.createComment(
                postId = "post-1",
                clientId = any(),
                body = "Risposta senza tag",
                entities = emptyList(),
                replyToCommentId = "comment-target",
            )
        }
    }

    @Test
    fun `canceling edit restores reply draft target and selection`() = runTest {
        val own = comment("own").copy(userId = "current-user")
        val target = comment("target")
        prepareInitialLoad(post(), comments = listOf(own, target))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.updateReplyDraft("Una risposta", emptyList(), 2, 5)
        vm.beginReply(target)
        val previous = vm.state.value.replyDraft
        assertEquals(true, vm.beginEdit(own))
        vm.updateReplyDraft("Modifica", emptyList(), 8, 8)
        assertEquals(true, vm.cancelEdit())
        assertEquals(previous, vm.state.value.replyDraft)
        assertEquals(null, vm.state.value.editingComment)
    }

    @Test
    fun `failed edit preserves edited text and retry restores previous draft`() = runTest {
        val own = comment("own").copy(userId = "current-user")
        prepareInitialLoad(post(), comments = listOf(own))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.updateReplyDraft("Bozza precedente", emptyList(), 2, 2)
        vm.beginEdit(own)
        vm.updateReplyDraft("Testo modificato", emptyList(), 16, 16)
        coEvery { postRepository.updateComment("post-1", "own", "Testo modificato", emptyList()) } throws IOException("offline")
        assertEquals(CommentsViewModel.ReplySubmission.EditSaving, vm.submitReply())
        assertEquals(null, vm.submitReply())
        assertEquals(false, vm.cancelEdit())
        advanceUntilIdle()
        assertEquals("Testo modificato", vm.state.value.replyDraft.text)
        assertEquals("own", vm.state.value.editingComment?.id)
        assertEquals(false, vm.state.value.isSavingCommentEdit)
        assertEquals(true, vm.state.value.replyError != null)
        coEvery { postRepository.updateComment("post-1", "own", "Testo modificato", emptyList()) } returns Unit
        vm.submitReply()
        advanceUntilIdle()
        assertEquals(null, vm.state.value.editingComment)
        assertEquals("Bozza precedente", vm.state.value.replyDraft.text)
        assertEquals(1, vm.state.value.editFinishedSignal)
    }

    @Test
    fun `submission clears draft and duplicate tap does not create another comment`() = runTest {
        prepareInitialLoad(post())
        coEvery { postRepository.createComment(any(), any(), "Risposta", emptyList(), null) } returns comment("sent")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.updateReplyDraft("  Risposta  ", emptyList(), 0, 0)
        assertEquals(CommentsViewModel.ReplySubmission.Sent, vm.submitReply())
        assertEquals(null, vm.submitReply())
        assertEquals("", vm.state.value.replyDraft.text)
        advanceUntilIdle()
        coVerify(exactly = 1) { postRepository.createComment(any(), any(), "Risposta", emptyList(), null) }
    }

    private fun prepareInitialLoad(
        post: Post,
        comments: List<Comment> = emptyList(),
    ) {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postRepository.getPost("post-1") } returns post
        coEvery { postRepository.getComments("post-1") } returns comments
    }

    private fun createViewModel() = CommentsViewModel(
        postRepo = postRepository,
        retryVoteUseCase = retryVoteUseCase,
        account = postAccount,
        savedState = SavedStateHandle(mapOf("postId" to "post-1")),
    )

    private fun commentsOf(viewModel: CommentsViewModel): List<CommentUiModel> =
        (viewModel.state.value.comments as UiState.Success).data

    private fun user() = PostAuthor(
        id = "current-user",
        nickname = "Gianluca",
    )

    private fun post() = Post(
        id = "post-1",
        userId = "author-1",
        universityId = "university-1",
        body = "Post",
        createdAt = "2026-08-14T00:00:00Z",
    )

    private fun comment(id: String) = Comment(
        id = id,
        postId = "post-1",
        userId = "author-2",
        body = "Commento",
        createdAt = "2026-08-14T00:00:00Z",
    )
}
