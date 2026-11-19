package com.axoo.features.posts.presentation.posteditor

import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.PostAccount
import com.axoo.features.posts.domain.PostAuthor
import com.axoo.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlinx.coroutines.test.runCurrent
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val postRepository = mockk<PostRepository>()
    private val postAccount = mockk<PostAccount>(relaxed = true)
    private val photoRepository = mockk<com.axoo.features.posts.data.PostPhotoRepository>(relaxed = true)

    @Test fun `AUP stickers keep WebP metadata when selected for a post`() = runTest {
        prepareProfile()
        val vm = createViewModel()
        advanceUntilIdle()
        val media = com.axoo.features.posts.domain.AupStickers.all[11]
        vm.selectRemoteMedia(media)
        val selected = checkNotNull(vm.state.value.draft.selectedMedia)
        assertEquals(PostMediaKind.Sticker, selected.kind)
        assertEquals("image/webp", selected.mimeType)
        assertEquals("aup", selected.provider)
        assertEquals("aup-12", selected.providerId)
        assertEquals(media.mediaUrl, selected.url)
        assertEquals(media.previewFallbackUrl, selected.previewFallbackUrl)
    }

    @Test
    fun `retrying unchanged publication reuses the same client id`() = runTest {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
        val clientIds = mutableListOf<String>()
        var attempt = 0
        coEvery {
            postRepository.postEditor(
                clientId = any(),
                body = "Ciao campus",
                entities = emptyList(),
                flair = null,
                media = null,
            )
        } coAnswers {
            clientIds += firstArg<String>()
            attempt++
            if (attempt == 1) throw IOException("offline")
            post()
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.publish(body = "Ciao campus")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.submission is PostEditorState.Error)

        viewModel.publish(body = "Ciao campus")
        advanceUntilIdle()

        assertEquals(2, clientIds.size)
        assertEquals(clientIds.first(), clientIds.last())
        assertEquals(post(), (viewModel.state.value.submission as PostEditorState.Success).post)
    }

    @Test
    fun `loading a post for editing exposes the loaded post`() = runTest {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
        coEvery { postRepository.getPost("post-1") } returns post()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadPostForEditing("post-1")
        advanceUntilIdle()

        assertEquals(post(), (viewModel.state.value.editPostLoad as EditPostLoadState.Loaded).post)
    }

    @Test
    fun `editing compares the entire draft and reverting changes disables saving`() = runTest {
        prepareProfile()
        val original = post().copy(flair = "meme")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Edit(original.id, original))
        assertFalse(vm.state.value.canPublish)
        assertFalse(vm.state.value.hasUnsavedChanges)
        vm.setBody("Changed", emptyList())
        assertTrue(vm.state.value.canPublish)
        vm.setBody(original.body, original.entities)
        assertFalse(vm.state.value.canPublish)
        vm.setFlair("spotted")
        assertTrue(vm.state.value.hasUnsavedChanges)
        vm.setFlair(original.flair)
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `edit draft stays unavailable until loading finishes and cannot overwrite a newer session`() = runTest {
        prepareProfile()
        lateinit var response: Continuation<Post>
        coEvery { postRepository.getPost("old") } coAnswers { suspendCoroutine { response = it } }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Edit("old"))
        runCurrent()
        assertFalse(vm.state.value.isEditorReady)
        vm.setBody("Too early", emptyList())
        assertEquals("", vm.state.value.draft.bodyText)
        vm.begin(PostEditorMode.Create)
        vm.setBody("New draft", emptyList())
        response.resume(post().copy(id = "old"))
        advanceUntilIdle()
        assertEquals("New draft", vm.state.value.draft.bodyText)
        assertFalse(vm.state.value.isEditing)
        assertTrue(vm.state.value.canPublish)
    }

    @Test
    fun `loading edit content preserves media metadata and initially disables saving`() = runTest {
        prepareProfile()
        val media = com.axoo.features.posts.domain.ImageMeta("https://media.test/meme.png", width = 640, height = 480, type = "meme")
        val original = post().copy(media = media)
        coEvery { postRepository.getPost(original.id) } returns original
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Edit(original.id))
        advanceUntilIdle()
        assertTrue(vm.state.value.isEditorReady)
        assertEquals(media.url, vm.state.value.draft.selectedMedia?.url)
        assertEquals(media.width, vm.state.value.draft.selectedMedia?.width)
        assertFalse(vm.state.value.canPublish)
        vm.removeMedia()
        assertTrue(vm.state.value.hasUnsavedChanges)
        assertTrue(vm.state.value.canPublish)
    }

    @Test
    fun `photo preparation blocks submission and closed session discards a late file`() = runTest {
        prepareProfile()
        val uri = mockk<android.net.Uri>()
        val photo = SelectedPostMedia(PostMediaKind.Photo, "file:///late.jpg", "file:///late.jpg")
        lateinit var response: Continuation<SelectedPostMedia>
        coEvery { photoRepository.prepare(uri) } coAnswers { suspendCoroutine { response = it } }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Create)
        vm.setBody("Photo draft", emptyList())
        vm.selectPhoto(uri)
        runCurrent()
        assertTrue(vm.state.value.isPreparingPhoto)
        assertFalse(vm.state.value.canPublish)
        vm.submitDraft()
        io.mockk.coVerify(exactly = 0) { postRepository.postEditor(any(), any(), any(), any(), any()) }
        vm.close()
        vm.begin(PostEditorMode.Create)
        vm.setBody("Other draft", emptyList())
        response.resume(photo)
        advanceUntilIdle()
        assertEquals("Other draft", vm.state.value.draft.bodyText)
        assertNull(vm.state.value.draft.selectedMedia)
        assertFalse(vm.state.value.isPreparingPhoto)
        io.mockk.verify(exactly = 1) { photoRepository.discard(photo) }
    }

    @Test
    fun `failed photo preparation retains the existing attachment and allows retry`() = runTest {
        prepareProfile()
        val originalMedia = com.axoo.features.posts.domain.ImageMeta("https://media.test/old.png", type = "meme")
        val original = post().copy(media = originalMedia)
        val uri = mockk<android.net.Uri>()
        val photo = SelectedPostMedia(PostMediaKind.Photo, "file:///new.jpg", "file:///new.jpg")
        coEvery { photoRepository.prepare(uri) } throws IOException("Cannot read photo")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Edit(original.id, original))
        vm.selectPhoto(uri)
        advanceUntilIdle()
        assertEquals(originalMedia.url, vm.state.value.draft.selectedMedia?.url)
        assertEquals("Cannot read photo", vm.state.value.photoError)
        assertFalse(vm.state.value.isPreparingPhoto)
        vm.consumePhotoError()
        coEvery { photoRepository.prepare(uri) } returns photo
        vm.selectPhoto(uri)
        advanceUntilIdle()
        assertEquals(photo, vm.state.value.draft.selectedMedia)
        assertNull(vm.state.value.photoError)
        assertTrue(vm.state.value.canPublish)
        vm.close()
        io.mockk.verify(exactly = 1) { photoRepository.discard(photo) }
    }

    @Test
    fun `draft submission trims text and shifts emoji entities while blocking edits`() = runTest {
        prepareProfile()
        val entity = com.axoo.engine.emoji.text.MessageEntity(offset = 2, length = 1, customEmojiId = 7)
        lateinit var response: Continuation<Post>
        coEvery { postRepository.postEditor(any(), any(), any(), any(), any()) } coAnswers {
            assertEquals("x", arg<String>(1))
            assertEquals(listOf(entity.copy(offset = 0)), arg<List<com.axoo.engine.emoji.text.MessageEntity>>(2))
            suspendCoroutine { response = it }
        }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Create)
        vm.setBody("  x  ", listOf(entity))
        vm.submitDraft()
        runCurrent()
        vm.setBody("Cannot change", emptyList())
        vm.setFlair("spotted")
        assertEquals("  x  ", vm.state.value.draft.bodyText)
        assertNull(vm.state.value.draft.selectedFlair)
        vm.submitDraft()
        io.mockk.coVerify(exactly = 1) { postRepository.postEditor(any(), any(), any(), any(), any()) }
        response.resume(post())
        advanceUntilIdle()
        assertTrue(vm.state.value.submission is PostEditorState.Success)
    }

    @Test
    fun `closing during publication ignores a late response in a new draft`() = runTest {
        prepareProfile()
        lateinit var response: Continuation<Post>
        coEvery { postRepository.postEditor(any(), any(), any(), any(), any()) } coAnswers {
            suspendCoroutine { response = it }
        }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Create)
        vm.setBody("Submitting", emptyList())
        vm.submitDraft()
        runCurrent()
        vm.close()
        vm.begin(PostEditorMode.Create)
        vm.setBody("Next", emptyList())
        response.resume(post())
        advanceUntilIdle()
        assertEquals("Next", vm.state.value.draft.bodyText)
        assertTrue(vm.state.value.submission is PostEditorState.Idle)
    }

    @Test
    fun `separate create and edit models preserve the create draft`() = runTest {
        prepareProfile()
        val create = createViewModel()
        val edit = createViewModel()
        advanceUntilIdle()
        create.begin(PostEditorMode.Create)
        create.setBody("Unpublished creation", emptyList())
        edit.begin(PostEditorMode.Edit(post().id, post()))
        edit.setBody("Edited post", emptyList())
        edit.close()
        assertEquals("Unpublished creation", create.state.value.draft.bodyText)
        assertTrue(create.state.value.hasUnsavedChanges)
        assertTrue(create.state.value.canPublish)
    }

    private fun prepareProfile() {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
    }

    @Test fun `poll submission trims each option and preserves animated emoji entities`() = runTest {
        prepareProfile()
        val entity = com.axoo.engine.emoji.text.MessageEntity(offset = 2, length = 2, customEmojiId = 7)
        coEvery { postRepository.postEditor(any(), any(), any(), any(), any(), any()) } coAnswers {
            val options = arg<List<com.axoo.features.posts.domain.PollOptionInput>>(5)
            assertEquals("🔥", options[0].text)
            assertEquals(listOf(entity.copy(offset = 0)), options[0].entities)
            assertEquals("Tè", options[1].text)
            post()
        }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Create)
        vm.setBody("Cosa scegli?", emptyList())
        vm.togglePoll()
        assertFalse(vm.state.value.canPublish)
        vm.setPollOption(0, "  🔥  ", listOf(entity))
        vm.setPollOption(1, " Tè ", emptyList())
        assertTrue(vm.state.value.canPublish)
        vm.submitDraft()
        advanceUntilIdle()
        assertTrue(vm.state.value.submission is PostEditorState.Success)
    }

    @Test fun `poll options are bounded and clearing removes the entire poll`() = runTest {
        prepareProfile()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePoll()
        assertTrue(vm.state.value.hasUnsavedChanges)
        repeat(6) { vm.addPollOption() }
        assertEquals(4, vm.state.value.draft.pollOptions!!.size)
        repeat(6) { vm.removePollOption(0) }
        assertEquals(2, vm.state.value.draft.pollOptions!!.size)
        vm.clearDraft()
        assertNull(vm.state.value.draft.pollOptions)
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test fun `published poll cannot initialize an edit session even through direct navigation`() = runTest {
        prepareProfile()
        val original = post().copy(poll = com.axoo.features.posts.domain.PostPoll())
        coEvery { postRepository.getPost(original.id) } returns original
        val vm = createViewModel()
        advanceUntilIdle()
        vm.begin(PostEditorMode.Edit(original.id, original))
        advanceUntilIdle()
        assertFalse(vm.state.value.isEditorReady)
        assertFalse(vm.state.value.canPublish)
        assertTrue(vm.state.value.editPostLoad is EditPostLoadState.Error)
        vm.setBody("Tentativo modifica", emptyList())
        assertEquals("", vm.state.value.draft.bodyText)
    }

    private fun createViewModel() = PostEditorViewModel(
        postRepo = postRepository,
        account = postAccount,
        photoRepo = photoRepository,
    )

    @Test
    fun `photo publish retry preserves upload and client id`() = runTest {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
        val selected = SelectedPostMedia(PostMediaKind.Photo, "file:///photo.jpg", "file:///photo.jpg")
        val uploaded = com.axoo.features.posts.domain.ImageMeta("https://example.com/photo.jpg", width = 800, height = 600, type = "photo")
        coEvery { photoRepository.upload(selected) } returns uploaded
        val ids = mutableListOf<String>()
        coEvery { postRepository.postEditor(any(), any(), any(), any(), any()) } coAnswers {
            ids += firstArg<String>()
            assertEquals(uploaded, arg<com.axoo.features.posts.domain.ImageMeta>(4))
            if (ids.size == 1) throw IOException("response lost")
            post()
        }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.publish("Foto campus", selectedMedia = selected)
        advanceUntilIdle()
        io.mockk.verify(exactly = 0) { photoRepository.discard(any()) }
        vm.publish("Foto campus", selectedMedia = selected)
        advanceUntilIdle()
        assertEquals(ids[0], ids[1])
        io.mockk.coVerify(exactly = 1) { photoRepository.upload(selected) }
        io.mockk.verify(exactly = 1) { photoRepository.discard(selected) }
        assertTrue(vm.state.value.submission is PostEditorState.Success)
    }

    @Test
    fun `upload failure does not submit a post or discard the photo`() = runTest {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
        val selected = SelectedPostMedia(PostMediaKind.Photo, "file:///photo.jpg", "file:///photo.jpg")
        coEvery { photoRepository.upload(selected) } throws IOException("offline")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.publish("Foto campus", selectedMedia = selected)
        advanceUntilIdle()
        assertTrue(vm.state.value.submission is PostEditorState.Error)
        io.mockk.coVerify(exactly = 0) { postRepository.postEditor(any(), any(), any(), any(), any()) }
        io.mockk.verify(exactly = 0) { photoRepository.discard(any()) }
    }

    @Test
    fun `reopening editor uses the latest shared profile`() = runTest {
        every { postAccount.cachedAuthor() } returns user()
        coEvery { postAccount.loadAuthor() } returns user()
        val vm = PostEditorViewModel(postRepository, postAccount, photoRepository)
        advanceUntilIdle()
        vm.begin(PostEditorMode.Create)
        every { postAccount.cachedAuthor() } returns user().copy(nickname = "Nickname aggiornato")
        vm.close()
        vm.begin(PostEditorMode.Create)
        assertEquals("Nickname aggiornato", vm.state.value.currentUser?.nickname)
    }

    private fun user() = PostAuthor(
        id = "current-user",
        nickname = "Gianluca",
    )

    private fun post() = Post(
        id = "post-1",
        userId = "current-user",
        universityId = "university-1",
        body = "Ciao campus",
        createdAt = "2026-08-14T00:00:00Z",
    )
}
