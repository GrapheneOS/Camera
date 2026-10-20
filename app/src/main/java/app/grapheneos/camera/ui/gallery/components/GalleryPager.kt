package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private val PAGE_SPACING = 24.dp

@Composable
internal fun GalleryPager(
    pages: ImmutableList<GalleryPageUiModel>,
    initialPage: Int,
    isZoomed: Boolean,
    onPageSelected: (Int) -> Unit,
    onTap: () -> Unit,
    onVideoClick: () -> Unit,
    onZoomChanged: (pageKey: String, isZoomed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size },
    )
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.targetPage.takeUnless { isDragged } }
            .filterNotNull()
            .distinctUntilChanged()
            .filter { it in pages.indices }
            .collect { page -> currentOnPageSelected(page) }
    }

    HorizontalPager(
        state = pagerState,
        key = { page -> pages[page].key },
        pageSpacing = PAGE_SPACING,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isZoomed,
        modifier = modifier,
    ) { page ->
        val pageModel = pages[page]

        GalleryPage(
            page = pageModel,
            onTap = onTap,
            onVideoClick = onVideoClick,
            onZoomChanged = { isPageZoomed ->
                onZoomChanged(pageModel.key, isPageZoomed)
            },
        )
    }
}
