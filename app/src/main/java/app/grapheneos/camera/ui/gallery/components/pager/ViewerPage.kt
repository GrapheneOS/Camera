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
import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanel
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerGestures
import app.grapheneos.camera.ui.gallery.components.pager.gesture.viewerGestureInput
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
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
    video: VideoPlaybackUiModel?,
    videoOverlay: (@Composable BoxScope.() -> Unit)?,
    onTap: () -> Unit,
    onVideoOutputChange: (VideoOutput?) -> Unit,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
) {
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
    val photoTransform = when {
        isCurrentPage -> {
            Modifier
                .viewerGestureInput(
                    gestures = gestures,
                    holdsVideo = video is VideoPlaybackUiModel.Active,
                )
                .graphicsLayer(viewerTransform::applyTo)
        }
        else -> Modifier
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
            video = video,
            mayRotateVideo = isCurrentPage && gestures.transformState.isTransforming,
            isVideoRotated = isCurrentPage && isRotated,
            onVideoOutputChange = onVideoOutputChange,
            modifier = Modifier
                .offset { detailsState.photoShiftAt(detailsPosition()).round() }
                .then(photoTransform),
        )
        if (video is VideoPlaybackUiModel.Active) {
            VideoSkipWaves(presses = skipPresses)
        }
        videoOverlay?.invoke(this)
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
