package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanelState
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.components.pager.gesture.rememberViewerGestures
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val CAMERA_PAGE_KEY = "camera"

private val PAGE_SPACING = 24.dp

@Composable
internal fun GalleryPager(
    pages: ImmutableList<GalleryPageUiModel>,
    previews: ImmutableMap<String, GalleryPreview>,
    details: ImmutableMap<String, GalleryDetails>,
    pagerState: PagerState,
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
    onCameraPageReached: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settledPage = pagerState.settledPage
    val settledItem = pages.getOrNull(pagerState.settledItemIndex)
    val gestures = rememberViewerGestures(
        transformState = transformState,
        detailsState = detailsState,
        onDetailsShownChange = onDetailsShownChange,
    )
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isLeavingToCamera by remember { mutableStateOf(false) }

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

    CameraPageEffect(
        pagerState = pagerState,
        onLeavingToCamera = { isLeavingToCamera = true },
        onCameraPageReached = onCameraPageReached,
    )

    ViewerLayoutEffect(
        transformState = transformState,
        detailsState = detailsState,
        containerSize = containerSize,
        page = settledItem,
        preview = settledItem?.let { previews[it.key] },
    )

    HorizontalPager(
        state = pagerState,
        key = { page -> pageKey(pages = pages, page = page) },
        pageSpacing = PAGE_SPACING,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isLeavingToCamera,
        modifier = modifier.onSizeChanged { containerSize = it },
    ) { index ->
        when (val page = pages.getOrNull(index - FIRST_ITEM_PAGE)) {
            null -> CameraPage()
            else -> ViewerPage(
                page = page,
                preview = previews[page.key] ?: GalleryPreview.Loading,
                details = details[page.key] ?: GalleryDetails.Loading,
                isCurrentPage = index == settledPage,
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
            .map { page -> page - FIRST_ITEM_PAGE }
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
                pagerState.requestScrollToPage(currentPage + FIRST_ITEM_PAGE)
            }
        }
    }
}

@Composable
private fun CameraPageEffect(
    pagerState: PagerState,
    onLeavingToCamera: () -> Unit,
    onCameraPageReached: () -> Unit,
) {
    val currentOnLeavingToCamera by rememberUpdatedState(onLeavingToCamera)
    val currentOnCameraPageReached by rememberUpdatedState(onCameraPageReached)
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState) {
        val isReleasedTowardsCamera = snapshotFlow {
            pagerState.isScrollInProgress && !isDragged && pagerState.targetPage == CAMERA_PAGE
        }
        val isScrolling = snapshotFlow { pagerState.isScrollInProgress }

        isReleasedTowardsCamera.first { it }
        currentOnLeavingToCamera()

        isScrolling.first { !it }
        pagerState.animateScrollToPage(CAMERA_PAGE)
        currentOnCameraPageReached()
    }
}

@Composable
private fun ViewerLayoutEffect(
    transformState: ViewerTransformState,
    detailsState: DetailsPanelState,
    containerSize: IntSize,
    page: GalleryPageUiModel?,
    preview: GalleryPreview?,
) {
    val imageSize = preview?.let(::imageSize)
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

private fun pageKey(
    pages: List<GalleryPageUiModel>,
    page: Int,
): String {
    return when (page) {
        CAMERA_PAGE -> CAMERA_PAGE_KEY
        else -> pages[page - FIRST_ITEM_PAGE].key
    }
}

private fun imageSize(preview: GalleryPreview): Size? {
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
