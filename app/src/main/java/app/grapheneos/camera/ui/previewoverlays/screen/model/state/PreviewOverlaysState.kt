package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import kotlin.time.Duration

internal data class PreviewOverlaysState(
    val settings: CameraSettings = CameraSettings(),
    val previewContent: PreviewContentState? = null,
    val isPreviewFlashVisible: Boolean = false,
    val displayRotation: DeviceOrientation = DeviceOrientation.DEGREES_0,
    val focus: FocusState = FocusState(),
    val level: LevelState = LevelState(),
    val activeControl: PreviewControl = PreviewControl.Zoom,
    val zoom: ZoomState = ZoomState(),
    val exposure: ExposureState = ExposureState(),
) {

    fun isPreviewInteractive(): Boolean {
        val content = previewContent ?: return false
        return content.mode != CameraMode.QR_SCAN &&
            content.isPreviewStreaming &&
            !isPreviewReplaced(content)
    }

    fun isPreviewInputAvailable(): Boolean {
        return isPreviewInteractive() && !isPreviewFlashVisible
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

    fun isZoomAvailable(): Boolean {
        val content = previewContent ?: return false
        return content.mode != CameraMode.QR_SCAN &&
            !content.isCaptureResultVisible &&
            !isPreviewFlashVisible &&
            zoom.isAdjustable()
    }

    fun isZoomVisible(): Boolean {
        return visibleControl() == PreviewControl.Zoom
    }

    fun isExposureVisible(): Boolean {
        return visibleControl() == PreviewControl.Exposure
    }

    fun visibleControl(): PreviewControl? {
        val defaultControl = PreviewControl.Zoom.takeIf { isZoomAvailable() }
        return when (activeControl) {
            PreviewControl.Zoom -> defaultControl
            PreviewControl.Exposure -> PreviewControl.Exposure.takeIf {
                isPreviewInputAvailable() && exposure.value?.isAdjustable() == true
            } ?: defaultControl
        }
    }

    fun withPerFrameValuesOf(other: PreviewOverlaysState): PreviewOverlaysState {
        return copy(
            level = other.level,
            zoom = zoom.withPerFrameValuesOf(other.zoom),
        )
    }

    fun withVisibleControlActive(): PreviewOverlaysState {
        return copy(activeControl = visibleControl() ?: PreviewControl.Zoom)
    }

    private fun isPreviewHidden(content: PreviewContentState): Boolean {
        return isPreviewReplaced(content) || isPreviewFlashVisible
    }

    private fun isPreviewReplaced(content: PreviewContentState): Boolean {
        return content.isTransitionFrameVisible || content.isCaptureResultVisible
    }
}
