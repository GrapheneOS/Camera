package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import app.grapheneos.camera.ui.gallery.components.gesture.ViewerGestures
import app.grapheneos.camera.ui.gallery.components.gesture.viewerGestureInput
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel

@Composable
internal fun ViewerPage(
    page: GalleryPageUiModel,
    isCurrentPage: Boolean,
    showsDetails: Boolean,
    gestures: ViewerGestures,
    viewerTransform: ViewerTransform,
    detailsProgress: () -> Float,
    onTap: () -> Unit,
    onVideoClick: () -> Unit,
) {
    val detailsState = gestures.detailsState
    val photoTransform = when {
        isCurrentPage -> {
            Modifier
                .viewerGestureInput(gestures)
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
            onTap = onTap,
            onDoubleTap = when {
                isCurrentPage && !showsDetails -> gestures.transformState::applyDoubleTap
                else -> null
            },
            onVideoClick = onVideoClick,
            modifier = Modifier
                .graphicsLayer { translationY = detailsState.photoShift(detailsProgress()) }
                .then(photoTransform),
        )
        DetailsPanel(
            detailsState = detailsState,
            progress = detailsProgress,
            details = page.details,
            isVideo = page.isVideo,
            uri = page.key,
            onShownChange = gestures.onDetailsShownChange,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
