package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.GridType

internal data class PreviewOverlaysState(
    val gridType: GridType = GridType.NONE,
    val previewContent: PreviewContentState? = null,
    val isPreviewFlashVisible: Boolean = false,
) {

    fun showsGrid(): Boolean {
        val content = previewContent ?: return false
        val isGridAvailable = when (content.mode) {
            CameraMode.QR_SCAN -> false
            else -> true
        }

        return gridType != GridType.NONE &&
            isGridAvailable &&
            !content.isTransitionFrameVisible &&
            !content.isCaptureResultVisible &&
            !isPreviewFlashVisible
    }
}
