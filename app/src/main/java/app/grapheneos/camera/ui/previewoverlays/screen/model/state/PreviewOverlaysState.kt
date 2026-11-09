package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import kotlin.time.Duration

internal data class PreviewOverlaysState(
    val settings: CameraSettings = CameraSettings(),
    val focus: FocusState = FocusState(),
    val previewContent: PreviewContentState? = null,
    val isPreviewFlashVisible: Boolean = false,
    val level: LevelState = LevelState(),
    val displayRotation: DeviceOrientation = DeviceOrientation.DEGREES_0,
) {

    fun isFocusAvailable(): Boolean {
        return isPreviewFocusable() && !isPreviewFlashVisible
    }

    fun isPreviewFocusable(): Boolean {
        val content = previewContent ?: return false
        return content.mode != CameraMode.QR_SCAN &&
            content.isPreviewStreaming &&
            !isPreviewReplaced(content)
    }

    fun isGridVisible(): Boolean {
        val content = previewContent ?: return false
        return settings.gridType != GridType.NONE &&
            content.mode != CameraMode.QR_SCAN &&
            !isPreviewHidden(content)
    }

    fun visibleSelfTimerRemaining(): Duration? {
        val content = previewContent ?: return null
        return when {
            content.mode == CameraMode.QR_SCAN || isPreviewHidden(content) -> null
            else -> content.selfTimerRemaining
        }
    }

    fun isLevelVisible(): Boolean {
        return isLevelAvailable() && level.visible
    }

    fun isLevelAvailable(): Boolean {
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

        return settings.gyroscopeSuggestions && isPhotoMode &&
            content.lensFacing == LensFacing.BACK &&
            content.isPreviewStreaming &&
            content.selfTimerRemaining == null &&
            !content.isTakingPicture &&
            !isPreviewHidden(content)
    }

    private fun isPreviewHidden(content: PreviewContentState): Boolean {
        return isPreviewReplaced(content) || isPreviewFlashVisible
    }

    private fun isPreviewReplaced(content: PreviewContentState): Boolean {
        return content.isTransitionFrameVisible || content.isCaptureResultVisible
    }
}
