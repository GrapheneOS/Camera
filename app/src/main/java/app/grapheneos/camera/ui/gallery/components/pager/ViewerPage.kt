package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.round
import app.grapheneos.camera.data.media.model.VideoOutputChange
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanel
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerGestures
import app.grapheneos.camera.ui.gallery.components.pager.gesture.viewerGestureInput
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryVideoUiModel
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel

@Composable
internal fun ViewerPage(
    page: GalleryPageUiModel,
    preview: GalleryPreview,
    details: GalleryDetails,
    isCurrentPage: Boolean,
    showsDetails: Boolean,
    gestures: ViewerGestures,
    viewerTransform: ViewerTransform,
    detailsPosition: () -> Float,
    video: GalleryVideoUiModel?,
    videoOverlay: @Composable BoxScope.() -> Unit,
    onTap: () -> Unit,
    onVideoOutputChange: (VideoOutputChange) -> Unit,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
) {
    val playback = video?.playback
    val detailsState = gestures.detailsState
    val isRotated by remember(viewerTransform) { derivedStateOf { viewerTransform.isRotated() } }
    val skipPresses = remember { VideoSkipPresses() }
    val handlesTaps = isCurrentPage && !showsDetails
    val onVideoSkip = { direction: SkipDirection, positionInArea: Offset ->
        skipPresses.press(
            direction = direction,
            positionInArea = positionInArea,
        )
        gestures.onVideoSkip(direction)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        GalleryPage(
            page = page,
            preview = preview,
            onTap = onTap,
            onDoubleTap = gestures.transformState::applyDoubleTap.takeIf { handlesTaps },
            onSkip = onVideoSkip.takeIf { handlesTaps },
            playback = playback,
            mayRotateVideo = isCurrentPage && gestures.transformState.isTransforming,
            isVideoRotated = isCurrentPage && isRotated,
            onVideoOutputChange = onVideoOutputChange,
            modifier = Modifier
                .offset { detailsState.photoShiftAt(detailsPosition()).round() }
                .currentPageTransform(
                    isCurrentPage = isCurrentPage,
                    gestures = gestures,
                    viewerTransform = viewerTransform,
                    holdsVideo = playback is VideoPlaybackUiModel.Active,
                ),
        )
        if (playback is VideoPlaybackUiModel.Active) {
            VideoSkipWaves(presses = skipPresses)
        }
        if (video != null) {
            videoOverlay()
        }
        DetailsPanel(
            detailsState = detailsState,
            position = detailsPosition,
            details = details,
            isVideo = page.isVideo,
            uri = page.key,
            isCurrentPage = isCurrentPage,
            onShownChange = gestures.onDetailsShownChange,
            onOpenMapClick = onOpenMapClick,
            onCopy = onCopy,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

private fun Modifier.currentPageTransform(
    isCurrentPage: Boolean,
    gestures: ViewerGestures,
    viewerTransform: ViewerTransform,
    holdsVideo: Boolean,
): Modifier {
    return when {
        isCurrentPage -> {
            viewerGestureInput(
                gestures = gestures,
                holdsVideo = holdsVideo,
            ).graphicsLayer(viewerTransform::applyTo)
        }
        else -> this
    }
}
