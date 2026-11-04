package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.GridType
import kotlin.time.Duration

internal data class PreviewOverlaysState(
    val gridType: GridType = GridType.NONE,
    val previewContent: PreviewContentState? = null,
    val isPreviewFlashVisible: Boolean = false,
    val level: LevelState = LevelState(),
    val displayRotation: DeviceOrientation = DeviceOrientation.DEGREES_0,
) {

    fun resetLevelIfUnavailable(): PreviewOverlaysState {
        return when {
            canShowLevel() -> this
            else -> resetLevel()
        }
    }

    fun resetLevel(): PreviewOverlaysState {
        return copy(level = LevelState(isEnabled = level.isEnabled))
    }

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

    fun visibleSelfTimerRemaining(): Duration? {
        val content = previewContent ?: return null

        return when {
            content.mode == CameraMode.QR_SCAN ||
                content.isTransitionFrameVisible ||
                content.isCaptureResultVisible ||
                isPreviewFlashVisible -> null

            else -> content.selfTimerRemaining
        }
    }

    fun showsLevelOverlay(): Boolean {
        return canShowLevel() && level.visible
    }

    fun canShowLevel(): Boolean {
        val content = previewContent ?: return false
        val isPhotoMode = when (content.mode) {
            CameraMode.QR_SCAN -> false
            CameraMode.VIDEO -> false
            CameraMode.AUTO,
            CameraMode.FACE_RETOUCH,
            CameraMode.PORTRAIT,
            CameraMode.NIGHT,
            CameraMode.HDR,
            CameraMode.CAMERA,
            -> true
        }

        return level.isEnabled && isPhotoMode &&
            content.lensFacing == LensFacing.BACK &&
            content.isPreviewStreaming &&
            content.selfTimerRemaining == null &&
            !content.isTakingPicture &&
            !content.isTransitionFrameVisible &&
            !content.isCaptureResultVisible &&
            !isPreviewFlashVisible
    }
}
