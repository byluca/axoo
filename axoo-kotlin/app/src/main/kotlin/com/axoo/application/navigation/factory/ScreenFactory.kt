/**
 * Fabbrica delle interfacce grafiche.
 * Riceve una destinazione (Route) e costruisce la vista (View) corrispondente passandogli le dipendenze.
 */
package com.axoo.application.navigation.factory

// --- 1. Attrezzi base di Android ---
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity

// --- 2. Gestione della Memoria e Ciclo di Vita ---
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import coil.imageLoader
import coil.request.ImageRequest
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.theme.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// --- 3. Mappe e Destinazioni ---
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab

// --- 4. Le Schermate e i ViewModel (ordinati per feature) ---
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.features.auth.presentation.IntroHost
import com.axoo.features.auth.presentation.LoginHost
import com.axoo.features.auth.presentation.LoginViewModel
import com.axoo.features.home.presentation.FeedViewModel
import com.axoo.features.home.presentation.HomeHost
import com.axoo.features.moderation.presentation.ModerationHost
import com.axoo.features.moderation.presentation.ModerationViewModel
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.postdetail.CommentsViewModel
import com.axoo.features.posts.presentation.posteditor.PostEditorViewModel
import com.axoo.features.posts.presentation.media.RemoteMediaViewModel
import com.axoo.features.posts.presentation.postdetail.PostDetailHost
import com.axoo.features.posts.presentation.posteditor.PostEditorMode
import com.axoo.features.profile.presentation.blocked.BlockedUsersHost
import com.axoo.features.profile.presentation.myposts.MyPostsHost
import com.axoo.features.profile.presentation.myposts.MyPostsViewModel
import com.axoo.features.notifications.presentation.NotificationsViewModel
import com.axoo.features.notifications.presentation.NotificationsHost
import com.axoo.features.profile.presentation.ProfileHost
import com.axoo.features.profile.presentation.nickname.NicknameEditorHost
import com.axoo.features.profile.presentation.settings.ProfileSettingsHost
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.features.profile.presentation.PublicProfileHost
import com.axoo.features.profile.presentation.bio.BioEditorHost

interface Navigator {
    fun navigate(route: Route)
    fun replace(route: Route)
    fun navigateTab(tab: Tab, target: Route? = null)
    fun pop(): Boolean
}

interface ScreenFactory {
    fun create(context: Context, route: Route, navigator: Navigator): View
}

/** Crea le schermate e collega le destinazioni; menu e azioni appartengono alle feature. */
class DefaultScreenFactory(
    private val onAuthenticated: (SessionBootstrapResponse) -> Unit,
    private val onLogout: () -> Unit,
) : ScreenFactory {
    private var pendingEditPost: Post? = null

    override fun create(context: Context, route: Route, navigator: Navigator): View {
        // "Sbuccia" il Context generico per trovare la vera Activity (l'unica che possiede il dizionario dei ViewModel)
        val activity = requireComponentActivity(context)
        val viewModels = ViewModelProvider(activity) //crea un singolo dizionario globale (visto che la nostra architettura dell'app e' racchiusa in 1 activity)
        return when (route) {
            Route.Intro -> IntroHost(
                context = context,
                onNavigateToLogin = { navigator.replace(Route.Login) },
            )
            Route.Login -> {
                val loginVm = viewModels[LoginViewModel::class.java]
                loginVm.reset()
                LoginHost(
                    context = context,
                    viewModel = loginVm,
                    lifecycleOwner = activity,
                    onLoginSuccess = onAuthenticated,
                )
            }
            Route.Home -> {
                val feedVm = viewModels[FeedViewModel::class.java]
                activity.lifecycleScope.launch {
                    val user = viewModels[ProfileViewModel::class.java].me
                        .first { it.getOrNull() != null }.getOrNull() ?: return@launch
                    context.imageLoader.enqueue(ImageRequest.Builder(context)
                        .data(buildAvatarUrl(user.id, user.avatarConfig))
                        .size(context.dp(132f).toInt())
                        .allowHardware(false)
                        .build())
                }
                HomeHost(
                    context = context,
                    viewModel = feedVm,
                    lifecycleOwner = activity,
                    notifications = viewModels[NotificationsViewModel::class.java],
                ).also { host ->
                    val menu = FeedPostMenu(
                        host,
                        feedVm,
                        viewModels[ProfileViewModel::class.java],
                        viewModels[ModerationViewModel::class.java],
                        onEdit = { openPostEditor(it, navigator) },
                    )
                    host.setCallbacks(
                        onNavigate = { navigator.navigateTab(it) },
                        onPostOpen = { navigator.navigate(Route.PostDetail(it.id)) },
                        onPostMenu = menu::show,
                        onAuthor = { openProfile(it, navigator) },
                    )
                }
            }
            Route.Profile -> ProfileHost(
                context = context,
                viewModel = viewModels[ProfileViewModel::class.java],
                lifecycleOwner = activity,
                notifications = viewModels[NotificationsViewModel::class.java],
            ).also { host ->
                host.setCallbacks(
                    onTab = { navigator.navigateTab(it) },
                    onMenuTapped = { navigator.navigate(Route.ProfileSettings) },
                    onNicknameTapped = { navigator.navigate(Route.NicknameEditor) },
                    onMyPostsTapped = { navigator.navigate(Route.MyPosts) },
                    onBioTapped = { navigator.navigate(Route.BioEditor) },
                )
            }
            Route.Notifications -> NotificationsHost(
                context, viewModels[NotificationsViewModel::class.java], activity,
                onTab = { navigator.navigateTab(it) },
                onOpen = { navigator.navigate(Route.PostDetail(it.postId, commentId = it.commentId.takeIf(String::isNotBlank))) },
            )
            Route.MyPosts -> MyPostsHost(
                context, viewModels[MyPostsViewModel::class.java], activity,
                onBack = { navigator.pop() },
                onOpen = { navigator.navigate(Route.PostDetail(it.id)) },
                onAuthor = { openProfile(it, navigator) },
            ).also { host ->
                host.onMenu = FeedPostMenu(
                    host, viewModels[FeedViewModel::class.java], viewModels[ProfileViewModel::class.java],
                    viewModels[ModerationViewModel::class.java], onEdit = { openPostEditor(it, navigator) },
                )::show
            }
            Route.NicknameEditor -> NicknameEditorHost(
                context = context,
                viewModel = viewModels[ProfileViewModel::class.java],
                lifecycleOwner = activity,
                onBack = { navigator.pop() },
            )
            Route.BioEditor -> BioEditorHost(context, viewModels[ProfileViewModel::class.java], activity,
                onBack = { navigator.pop() })
            is Route.UserProfile -> PublicProfileHost(context, route.userId, viewModels[ProfileViewModel::class.java], activity,
                onBack = { navigator.pop() }, onEditBio = { navigator.navigate(Route.BioEditor) },
                postsModel = createUserPostsViewModel(activity, route.userId),
                onOpen = { navigator.navigate(Route.PostDetail(it.id)) },
                onAuthor = { if (it != route.userId) openProfile(it, navigator) },
            ).also { host ->
                host.onPostMenu = FeedPostMenu(host, viewModels[FeedViewModel::class.java],
                    viewModels[ProfileViewModel::class.java], viewModels[ModerationViewModel::class.java],
                    onEdit = { openPostEditor(it, navigator) })::show
            }
            Route.ProfileSettings -> ProfileSettingsHost(
                context = context,
                onLogout = onLogout,
                profileViewModel = viewModels[ProfileViewModel::class.java],
                lifecycleOwner = activity,
                onBack = { navigator.pop() },
                onNavigate = { navigator.navigate(it) },
                onThemeChanged = { activity.recreate() },
            )
            Route.BlockedUsers -> BlockedUsersHost(
                context = context,
                viewModel = viewModels[ProfileViewModel::class.java],
                lifecycleOwner = activity,
                onBack = { navigator.pop() },
            )
            Route.Moderation -> ModerationHost(
                context = context,
                viewModel = viewModels[ModerationViewModel::class.java],
                lifecycleOwner = activity,
                onBack = { navigator.pop() },
            )
            Route.PostEditor, is Route.EditPost -> {
                val mode = if (route is Route.EditPost) {
                    PostEditorMode.Edit(route.postId, pendingEditPost).also { pendingEditPost = null }
                } else {
                    PostEditorMode.Create
                }
                createPostEditor(
                    context = context,
                    mode = mode,
                    viewModel = viewModels[
                        if (route is Route.EditPost) "edit-post-${route.postId}" else "create-post",
                        PostEditorViewModel::class.java,
                    ],
                    remoteMediaViewModel = viewModels[RemoteMediaViewModel::class.java],
                    feedViewModel = viewModels[FeedViewModel::class.java],
                    lifecycleOwner = activity,
                    onClose = {
                        if (route is Route.EditPost) navigator.pop() else navigator.navigateTab(Tab.Home)
                    },
                )
            }
            is Route.PostDetail -> PostDetailHost(
                context = context,
                postId = route.postId,
                targetCommentId = route.commentId,
                viewModel = createCommentsViewModel(activity, route.postId),
                remoteMediaViewModel = viewModels[RemoteMediaViewModel::class.java],
                moderationViewModel = viewModels[ModerationViewModel::class.java],
                lifecycleOwner = activity,
            ).apply {
                val feedVm = viewModels[FeedViewModel::class.java]
                setCallbacks(
                    onBack = { navigator.pop() },
                    onEdit = { openPostEditor(it, navigator) },
                    onDeleted = feedVm::removePostFromFeeds,
                    onAuthor = { openProfile(it, navigator) },
                )
            }
        }
    }

    private fun openPostEditor(post: Post, navigator: Navigator) {
        if (post.poll != null) return
        pendingEditPost = post
        navigator.navigate(Route.EditPost(post.id))
    }

    private fun openProfile(userId: String, navigator: Navigator) {
        if (userId.isBlank()) return
        navigator.navigate(Route.UserProfile(userId))
    }
}

/**
 * Trucco magico per costruire il [CommentsViewModel] in modo autonomo.
 *
 * Siccome la libreria di Iniezione (Hilt) si aspetta che l'ID del post arrivi
 * dal sistema di navigazione nativo di Android (che noi non usiamo), gli
 * "iniettiamo" l'ID manualmente tramite i Creation Extras ([DEFAULT_ARGS_KEY]).
 *
 * Inoltre, usiamo l'ID del post come chiave per salvare il ViewModel nel dizionario
 * dell'Activity. In questo modo, se apri i commenti del Post 1 e del Post 2,
 * avranno due cervelli separati, ma se riapri il Post 1, ritroverai il cervello intatto.
 */
private fun createUserPostsViewModel(activity: ComponentActivity, userId: String): MyPostsViewModel {
    val extras = MutableCreationExtras(activity.defaultViewModelCreationExtras).apply {
        set(DEFAULT_ARGS_KEY, Bundle().apply { putString("authorId", userId) })
    }
    return ViewModelProvider(activity.viewModelStore, activity.defaultViewModelProviderFactory, extras)[
        "profile-posts-$userId", MyPostsViewModel::class.java]
}

private fun createCommentsViewModel(
    activity: ComponentActivity,
    postId: String,
): CommentsViewModel {
    val baseExtras = activity.defaultViewModelCreationExtras
    val extras = MutableCreationExtras(baseExtras).apply {
        set(DEFAULT_ARGS_KEY, Bundle().apply { putString("postId", postId) })
    }
    return ViewModelProvider(
        activity.viewModelStore,
        activity.defaultViewModelProviderFactory,
        extras,
    )["post-detail-$postId", CommentsViewModel::class.java]
}

private fun requireComponentActivity(context: Context): ComponentActivity {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("ScreenFactory requires a ComponentActivity in the context chain")
}
