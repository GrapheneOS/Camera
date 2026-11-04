package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.DeviceOrientation

internal sealed interface PreviewOverlaysAction {

    data object ScreenResumed : PreviewOverlaysAction

    data object ScreenPaused : PreviewOverlaysAction

    data class DisplayRotationChanged(
        val rotation: DeviceOrientation,
    ) : PreviewOverlaysAction

    data class PreviewContentChanged(
        val content: PreviewContentState,
    ) : PreviewOverlaysAction

    data class PreviewFlashVisibilityChanged(
        val visible: Boolean,
    ) : PreviewOverlaysAction
}
