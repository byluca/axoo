package com.axoo.features.posts.presentation.media.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.ui.sheets.BottomSheetConfig
import com.axoo.ui.sheets.BottomSheetContainer
import com.axoo.ui.theme.NativeColors

/** Native Material half/large sheet. Search IME, dragging and grid scroll use platform behavior. */
@SuppressLint("ViewConstructor") // Programmatic content only, never inflated from XML.
internal class RemoteMediaPickerSheet(
    context: Context,
    onMediaSelected: (RemoteMedia) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismissed: () -> Unit,
) : BottomSheetContainer(context, BottomSheetConfig(
    fillsHeight = true,
    sheetBackgroundColor = NativeColors.pickerSurface,
)) {
    private val content = RemoteMediaPickerPanelView(
        context = context,
        onRemoteMediaTap = { media -> dismissThen { onMediaSelected(media) } },
        onSearchQueryChanged = onSearchQueryChanged,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
    )
    init {
        setSheetContent(content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            content.setNavigationBottomInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        addOnDismissedListener {
            content.clearSearchFocus()
            onDismissed()
        }
    }
    fun bind(state: RemoteMediaState) = content.bind(state)
}
