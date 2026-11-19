package com.axoo.application.navigation.factory

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.axoo.features.posts.presentation.posteditor.PostEditorHost
import com.axoo.features.posts.presentation.posteditor.PostEditorMode
import com.axoo.features.posts.presentation.posteditor.PostEditorViewModel
import com.axoo.features.home.presentation.FeedViewModel
import com.axoo.features.posts.presentation.media.RemoteMediaViewModel
import com.axoo.ui.components.showMessage

/** Wiring condiviso tra creazione e modifica; la destinazione di uscita la decide il chiamante. */
internal fun createPostEditor(
    context: Context,
    mode: PostEditorMode,
    viewModel: PostEditorViewModel,
    remoteMediaViewModel: RemoteMediaViewModel,
    feedViewModel: FeedViewModel,
    lifecycleOwner: LifecycleOwner,
    onClose: () -> Unit,
): PostEditorHost {
    return PostEditorHost(
        context = context,
        mode = mode,
        viewModel = viewModel,
        remoteMediaViewModel = remoteMediaViewModel,
        lifecycleOwner = lifecycleOwner,
    ).apply {
        setCallbacks(
            onBack = onClose,
            onPublished = { post ->
                when (mode) {
                    PostEditorMode.Create -> feedViewModel.notifyNewPostPublished(post)
                    is PostEditorMode.Edit -> feedViewModel.updatePostInFeeds(post)
                }
                onClose()
            },
            onError = { showMessage(it) },
        )
    }
}
