package com.axoo.features.home.presentation

import com.axoo.features.home.domain.HomeFeedTab
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.data.PostsPage
import com.axoo.features.posts.domain.Post
import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.domain.User
import com.axoo.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val changes = kotlinx.coroutines.flow.MutableSharedFlow<com.axoo.features.posts.data.PostChange>()
    init { io.mockk.every { postRepository.changes } returns changes }
    private val profileRepository = mockk<ProfileRepository>()

    @Test
    fun `initial load exposes both feeds current user and unread count`() = runTest {
        val hotPost = post("hot-post")
        val newPost = post("new-post")
        prepareInitialLoad(hotPosts = listOf(hotPost), newPosts = listOf(newPost), unreadCount = 4)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(hotPost), state.hotFeed.posts)
        assertEquals(listOf(newPost), state.newFeed.posts)
        assertFalse(state.hotFeed.isLoading)
        assertFalse(state.newFeed.isLoading)
        assertEquals("current-user", state.currentUserId)
        assertEquals(4, state.newFeedUnreadCount)
    }

    @Test
    fun `failed post vote restores the post shown before the tap`() = runTest {
        val original = post(id = "post-1", upvotes = 2)
        prepareInitialLoad(hotPosts = listOf(original))
        coEvery { postRepository.votePost("post-1", 1) } throws IOException("offline")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.votePost(original, "upvote")

        assertEquals(3, viewModel.state.value.hotFeed.posts.single().upvotes)
        advanceUntilIdle()
        assertEquals(original, viewModel.state.value.hotFeed.posts.single())
    }

    @Test
    fun `load more appends new hot posts without duplicates`() = runTest {
        val first = post("post-1")
        val duplicate = post("post-2")
        val next = post("post-3")
        prepareInitialLoad(
            hotPosts = listOf(first, duplicate),
            hotCursor = "cursor-1",
        )
        coEvery {
            postRepository.getPostsPage("hot", "cursor-1", 20)
        } returns PostsPage(listOf(duplicate, next), "cursor-2")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMoreHot()
        advanceUntilIdle()

        assertEquals(listOf("post-1", "post-2", "post-3"), viewModel.state.value.hotFeed.posts.map(Post::id))
        assertEquals("cursor-2", viewModel.state.value.hotFeed.nextCursor)
        assertFalse(viewModel.state.value.hotFeed.isLoadingMore)
    }

    @Test
    fun `load more ignores repeated requests while one is running`() = runTest {
        prepareInitialLoad(hotPosts = listOf(post("post-1")), hotCursor = "cursor-1")
        val response = CompletableDeferred<PostsPage>()
        coEvery {
            postRepository.getPostsPage("hot", "cursor-1", 20)
        } coAnswers { response.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMoreHot()
        viewModel.loadMoreHot()
        runCurrent()

        assertTrue(viewModel.state.value.hotFeed.isLoadingMore)
        coVerify(exactly = 1) {
            postRepository.getPostsPage("hot", "cursor-1", 20)
        }

        response.complete(PostsPage(emptyList(), null))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hotFeed.isLoadingMore)
    }

    @Test
    fun `failed load more can be retried`() = runTest {
        prepareInitialLoad(hotPosts = listOf(post("post-1")), hotCursor = "cursor-1")
        var attempts = 0
        coEvery {
            postRepository.getPostsPage("hot", "cursor-1", 20)
        } coAnswers {
            attempts++
            if (attempts == 1) throw IOException("offline")
            PostsPage(listOf(post("post-2")), null)
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMoreHot()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hotFeed.isLoadingMore)
        assertEquals("cursor-1", viewModel.state.value.hotFeed.nextCursor)

        viewModel.loadMoreHot()
        advanceUntilIdle()
        assertEquals(listOf("post-1", "post-2"), viewModel.state.value.hotFeed.posts.map(Post::id))
        assertEquals(2, attempts)
    }

    @Test
    fun `opening new tab revalidates before marking it seen`() = runTest {
        val oldPosts = listOf(post("new-1"), post("new-2"))
        prepareInitialLoad(newPosts = oldPosts, newCursor = "new-cursor")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectFeedTab(HomeFeedTab.New)
        advanceUntilIdle()

        assertEquals(oldPosts, viewModel.state.value.newFeed.posts)
        assertEquals("new-cursor", viewModel.state.value.newFeed.nextCursor)
        coVerify(exactly = 2) { postRepository.getPostsPage(order = "new") }
        coVerify(exactly = 1) { postRepository.markNewFeedSeen(oldPosts.first().createdAt) }
    }

    @Test
    fun `feeds can paginate independently while repeated requests are ignored`() = runTest {
        val hot = post("hot-1")
        val new = post("new-1")
        prepareInitialLoad(listOf(hot), listOf(new), "hot-cursor", "new-cursor")
        val hotResponse = CompletableDeferred<PostsPage>()
        val newResponse = CompletableDeferred<PostsPage>()
        coEvery { postRepository.getPostsPage("hot", "hot-cursor", 20) } coAnswers { hotResponse.await() }
        coEvery { postRepository.getPostsPage("new", "new-cursor", 20) } coAnswers { newResponse.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(2) {
            viewModel.loadMoreHot()
            viewModel.loadMoreNew()
        }
        runCurrent()
        assertTrue(viewModel.state.value.hotFeed.isLoadingMore)
        assertTrue(viewModel.state.value.newFeed.isLoadingMore)

        newResponse.complete(PostsPage(listOf(new, post("new-2")), null))
        runCurrent()
        assertEquals(listOf("new-1", "new-2"), viewModel.state.value.newFeed.posts.map(Post::id))
        assertFalse(viewModel.state.value.newFeed.isLoadingMore)
        assertTrue(viewModel.state.value.hotFeed.isLoadingMore)

        hotResponse.complete(PostsPage(listOf(post("hot-2")), null))
        advanceUntilIdle()
        assertEquals(listOf("hot-1", "hot-2"), viewModel.state.value.hotFeed.posts.map(Post::id))
        coVerify(exactly = 1) { postRepository.getPostsPage("hot", "hot-cursor", 20) }
        coVerify(exactly = 1) { postRepository.getPostsPage("new", "new-cursor", 20) }
    }

    @Test
    fun `publishing preserves the new post until the server feed includes it`() = runTest {
        val hot = post("hot-1")
        val new = post("new-1")
        val published = post("published")
        prepareInitialLoad(hotPosts = listOf(hot), newPosts = listOf(new), unreadCount = 4)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.notifyNewPostPublished(published)
        advanceUntilIdle()

        assertEquals(listOf(published, new), viewModel.state.value.newFeed.posts)
        assertEquals(listOf(hot), viewModel.state.value.hotFeed.posts)
        assertEquals(HomeFeedTab.New, viewModel.state.value.selectedFeedTab)
        assertEquals(4, viewModel.state.value.newFeedUnreadCount) // Follow the server count, not a blind reset.
        coVerify(exactly = 2) { postRepository.getPostsPage(order = "new") }
        coVerify(exactly = 1) { postRepository.markNewFeedSeen(new.createdAt) }

        coEvery { postRepository.getPostsPage(order = "new") } returns
            PostsPage(listOf(published, new), null)
        viewModel.loadNew()
        advanceUntilIdle()
        assertEquals(listOf(published, new), viewModel.state.value.newFeed.posts)
    }

    @Test
    fun `hidden posts disappear from both feeds and stay hidden on refresh`() = runTest {
        val hidden = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
        prepareInitialLoad(hotPosts = listOf(post("post-1")), newPosts = listOf(post("post-1")))
        every { postRepository.hiddenPostIds } returns hidden
        val viewModel = createViewModel()
        advanceUntilIdle()
        hidden.value = setOf("post-1")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hotFeed.posts.isEmpty())
        assertTrue(viewModel.state.value.newFeed.posts.isEmpty())
        viewModel.loadInitialFeeds()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hotFeed.posts.isEmpty())
        assertTrue(viewModel.state.value.newFeed.posts.isEmpty())
    }

    private fun prepareInitialLoad(
        hotPosts: List<Post> = emptyList(),
        newPosts: List<Post> = emptyList(),
        hotCursor: String? = null,
        newCursor: String? = null,
        unreadCount: Int = 0,
    ) {
        every { postRepository.blockedAuthorIds } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        every { postRepository.hiddenPostIds } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        every { profileRepository.getCachedUser() } returns user()
        coEvery { profileRepository.getUniversityId() } returns "university-1"
        coEvery { postRepository.getPostsPage(order = "hot") } returns PostsPage(hotPosts, hotCursor)
        coEvery { postRepository.getPostsPage(order = "new") } returns PostsPage(newPosts, newCursor)
        coEvery { postRepository.getNewFeedUnreadCount() } returns unreadCount
    }

    private fun createViewModel() = FeedViewModel(
        postRepo = postRepository,
        profileRepo = profileRepository,
    )

    private fun user() = User(
        id = "current-user",
        nickname = "Gianluca",
        universityId = "university-1",
        createdAt = "2026-08-14T00:00:00Z",
    )

    private fun post(id: String, upvotes: Int = 0) = Post(
        id = id,
        userId = "author-1",
        nickname = "Autore",
        universityId = "university-1",
        body = "Corpo del post",
        upvotes = upvotes,
        createdAt = "2026-08-14T00:00:00Z",
    )
}
