package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanelState
import app.grapheneos.camera.ui.gallery.components.pager.GalleryPager
import app.grapheneos.camera.ui.gallery.components.pager.ViewerTransform
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.DetailsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.PageAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.ScreenAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.VideoAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState
import kotlin.time.Duration

@Composable
internal fun GalleryViewerPager(
    uiState: UiState,
    videoPosition: () -> Duration,
    pagerState: PagerState,
    transformState: ViewerTransformState,
    viewerTransform: ViewerTransform,
    detailsState: DetailsPanelState,
    detailsPosition: () -> Float,
    isPanelVisible: Boolean,
    contentPadding: PaddingValues,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val openMap = { onAction(DetailsAction.OpenMapClicked) }

    GalleryPager(
        pages = uiState.pager.pages,
        previews = uiState.pager.previews,
        details = uiState.pager.details,
        pagerState = pagerState,
        currentPage = uiState.pager.currentPage,
        showsDetails = uiState.showsDetails,
        transformState = transformState,
        viewerTransform = viewerTransform,
        detailsState = detailsState,
        detailsPosition = detailsPosition,
        video = uiState.video,
        videoOverlay = {
            uiState.video?.let { video ->
                GalleryVideoOverlay(
                    video = video,
                    position = videoPosition,
                    showsControls = uiState.chrome.showsVideoControls && !isPanelVisible,
                    contentPadding = contentPadding,
                    fadeProgress = viewerTransform::fadeProgress,
                    onAction = onAction,
                )
            }
        },
        onPageSelected = { page -> onAction(PageAction.PageSelected(page)) },
        onPageSettled = { page -> onAction(PageAction.PageSettled(page)) },
        onDetailsShownChange = { isShown -> onAction(DetailsAction.DetailsShownChanged(isShown)) },
        onTap = { onAction(ScreenAction.ScreenTapped) },
        onVideoOutputChange = { change -> onAction(VideoAction.VideoOutputChanged(change)) },
        onVideoHoldChange = { isHeld ->
            onAction(
                when {
                    isHeld -> VideoAction.VideoHoldStarted
                    else -> VideoAction.VideoHoldEnded
                },
            )
        },
        onVideoSkip = { direction -> onAction(VideoAction.SkipTapped(direction)) },
        onOpenMapClick = openMap.takeIf { uiState.pager.canOpenMap },
        onCopy = { text -> onAction(DetailsAction.DetailLongPressed(text)) },
        onCameraPageReached = onNavigateBack,
        modifier = Modifier.fillMaxSize(),
    )
}
