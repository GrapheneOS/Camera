package app.grapheneos.camera.ui.gallery.components

import android.view.ViewConfiguration
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.grapheneos.camera.ui.gallery.components.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.components.gesture.viewerTransformInput
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
    transformState: ViewerTransformState,
    viewerTransform: ViewerTransform,
    onPageSelected: (Int) -> Unit,
    onTap: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size },
    )
    val minScalingSpan = rememberMinScalingSpan()
    val currentPageTransform = remember(
        transformState,
        viewerTransform,
        minScalingSpan,
        hapticFeedback,
    ) {
        Modifier.viewerTransform(
            transformState = transformState,
            viewerTransform = viewerTransform,
            minScalingSpan = minScalingSpan,
            hapticFeedback = hapticFeedback,
        )
    }
    val isZoomed by remember(transformState) { derivedStateOf { transformState.isZoomed() } }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    PageSelectedEffect(
        pagerState = pagerState,
        pageCount = pages.size,
        onPageSelected = onPageSelected,
    )

    ViewerLayoutEffect(
        transformState = transformState,
        containerSize = containerSize,
        imageSize = pages.getOrNull(currentPage)?.let(::zoomableImageSize),
    )

    HorizontalPager(
        state = pagerState,
        key = { page -> pages[page].key },
        pageSpacing = PAGE_SPACING,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isZoomed,
        modifier = modifier.onSizeChanged { containerSize = it },
    ) { page ->
        val isCurrentPage = page == currentPage

        GalleryPage(
            page = pages[page],
            onTap = onTap,
            onDoubleTap = { position ->
                if (isCurrentPage) {
                    transformState.applyDoubleTap(position)
                }
            },
            onVideoClick = onVideoClick,
            modifier = when {
                isCurrentPage -> currentPageTransform
                else -> Modifier
            },
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

@Composable
private fun ViewerLayoutEffect(
    transformState: ViewerTransformState,
    containerSize: IntSize,
    imageSize: Size?,
) {
    LaunchedEffect(transformState, containerSize, imageSize) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect

        transformState.updateLayout(
            containerSize = containerSize.toSize(),
            imageSize = imageSize,
        )
    }
}

@Composable
private fun rememberMinScalingSpan(): Float {
    val context = LocalContext.current

    return remember(context) {
        ViewConfiguration.get(context).scaledMinimumScalingSpan.toFloat()
    }
}

private fun Modifier.viewerTransform(
    transformState: ViewerTransformState,
    viewerTransform: ViewerTransform,
    minScalingSpan: Float,
    hapticFeedback: HapticFeedback,
): Modifier {
    return viewerTransformInput(
        transformState = transformState,
        minScalingSpan = minScalingSpan,
        hapticFeedback = hapticFeedback,
    ).graphicsLayer(viewerTransform::applyTo)
}

private fun zoomableImageSize(page: GalleryPageUiModel): Size? {
    val preview = page.preview

    return when {
        page.isVideo || preview !is GalleryPreview.Loaded -> null
        else -> Size(
            width = preview.image.width.toFloat(),
            height = preview.image.height.toFloat(),
        )
    }
}
