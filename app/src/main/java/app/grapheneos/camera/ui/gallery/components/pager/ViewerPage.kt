package app.grapheneos.camera.ui.gallery.components.pager

import android.view.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.round
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanel
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerGestures
import app.grapheneos.camera.ui.gallery.components.pager.gesture.viewerGestureInput
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
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
    onVideoSurfaceChange: (Surface?) -> Unit,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
) {
    val detailsState = gestures.detailsState
    val handlesTaps = isCurrentPage && !showsDetails
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
            onSkip = gestures.onVideoSkip.takeIf { handlesTaps },
            video = video,
            onVideoSurfaceChange = onVideoSurfaceChange,
            modifier = Modifier
                .offset { detailsState.photoShiftAt(detailsPosition()).round() }
                .then(photoTransform),
        )
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
