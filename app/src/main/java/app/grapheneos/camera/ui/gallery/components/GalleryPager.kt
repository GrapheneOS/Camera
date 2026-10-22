package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.grapheneos.camera.ui.gallery.components.gesture.DetailsPanelState
import app.grapheneos.camera.ui.gallery.components.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.components.gesture.rememberViewerGestures
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private val PAGE_SPACING = 24.dp

@Composable
internal fun GalleryPager(
    pages: ImmutableList<GalleryPageUiModel>,
    initialPage: Int,
    currentPage: Int,
    showsDetails: Boolean,
    transformState: ViewerTransformState,
    viewerTransform: ViewerTransform,
    detailsState: DetailsPanelState,
    detailsPosition: () -> Float,
    onPageSelected: (Int) -> Unit,
    onDetailsShownChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onVideoClick: () -> Unit,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size },
    )
    val gestures = rememberViewerGestures(
        transformState = transformState,
        detailsState = detailsState,
        onDetailsShownChange = onDetailsShownChange,
    )
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    PageSelectedEffect(
        pagerState = pagerState,
        pageCount = pages.size,
        onPageSelected = onPageSelected,
    )

    PageListChangedEffect(
        pagerState = pagerState,
        pages = pages,
        currentPage = currentPage,
    )

    ViewerLayoutEffect(
        transformState = transformState,
        detailsState = detailsState,
        containerSize = containerSize,
        page = pages.getOrNull(currentPage),
    )

    HorizontalPager(
        state = pagerState,
        key = { page -> pages[page].key },
        pageSpacing = PAGE_SPACING,
        beyondViewportPageCount = 1,
        modifier = modifier.onSizeChanged { containerSize = it },
    ) { page ->
        ViewerPage(
            page = pages[page],
            isCurrentPage = page == currentPage,
            showsDetails = showsDetails,
            gestures = gestures,
            viewerTransform = viewerTransform,
            detailsPosition = detailsPosition,
            onTap = onTap,
            onVideoClick = onVideoClick,
            onOpenMapClick = onOpenMapClick,
            onCopy = onCopy,
        )
    }
}

@Composable
private fun PageSelectedEffect(
    pagerState: PagerState,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
) {
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.targetPage.takeUnless { isDragged } }
            .filterNotNull()
            .distinctUntilChanged()
            .filter { it in 0 until pageCount }
            .collect { page -> currentOnPageSelected(page) }
    }
}

// Keys would keep the pager on the page it showed, off an item restored before it.
@Composable
private fun PageListChangedEffect(
    pagerState: PagerState,
    pages: ImmutableList<GalleryPageUiModel>,
    currentPage: Int,
) {
    val pageKeys = remember(pages) { pages.map { it.key } }
    val shownPageKeys = remember { ShownPageKeys(pageKeys) }

    SideEffect {
        if (shownPageKeys.keys != pageKeys) {
            shownPageKeys.keys = pageKeys

            if (!pagerState.isScrollInProgress) {
                pagerState.requestScrollToPage(currentPage)
            }
        }
    }
}

@Composable
private fun ViewerLayoutEffect(
    transformState: ViewerTransformState,
    detailsState: DetailsPanelState,
    containerSize: IntSize,
    page: GalleryPageUiModel?,
) {
    val imageSize = page?.let(::imageSize)
    val canZoom = page?.isVideo == false

    LaunchedEffect(
        transformState,
        detailsState,
        containerSize,
        imageSize,
        canZoom,
    ) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect

        transformState.updateLayout(
            containerSize = containerSize.toSize(),
            imageSize = imageSize,
            canZoom = canZoom,
        )
        detailsState.updateLayout(containerSize.toSize())
    }
}

private fun imageSize(page: GalleryPageUiModel): Size? {
    val preview = page.preview

    return when {
        preview !is GalleryPreview.Loaded -> null
        else -> Size(
            width = preview.image.width.toFloat(),
            height = preview.image.height.toFloat(),
        )
    }
}

private class ShownPageKeys(
    var keys: List<String>,
)
