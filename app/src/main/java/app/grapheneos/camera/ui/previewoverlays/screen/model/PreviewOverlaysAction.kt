package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.ui.previewoverlays.screen.model.input.ZoomDirection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState

internal sealed interface PreviewOverlaysAction {

    sealed interface LifecycleAction : PreviewOverlaysAction {

        data object ScreenResumed : LifecycleAction

        data object ScreenPaused : LifecycleAction
    }

    sealed interface PreviewAction : PreviewOverlaysAction {

        data object FocusKeyPressed : PreviewAction

        data object PreviewTapped : PreviewAction

        data object PreviewLongPressed : PreviewAction

        data object PreviewGestureStarted : PreviewAction

        data class PreviewPressed(
            val x: Float,
            val y: Float,
            val hitRadius: Float,
        ) : PreviewAction

        data class DisplayRotationChanged(
            val rotation: DeviceOrientation,
        ) : PreviewAction

        data class PreviewContentChanged(
            val content: PreviewContentState,
        ) : PreviewAction

        data class PreviewFlashVisibilityChanged(
            val visible: Boolean,
        ) : PreviewAction
    }

    sealed interface ZoomAction : PreviewOverlaysAction {

        data object VolumeKeyReleased : ZoomAction

        data class ZoomKeyPressed(
            val direction: ZoomDirection,
        ) : ZoomAction

        data class VolumeKeyPressed(
            val direction: ZoomDirection,
        ) : ZoomAction

        data object Expanded : ZoomAction

        data class RatioChanged(
            val ratio: Float,
        ) : ZoomAction

        data class PresetSelected(
            val ratio: Float,
        ) : ZoomAction

        data object PreviewPinchStarted : ZoomAction

        data class PreviewPinchScaleChanged(
            val scaleFactor: Float,
        ) : ZoomAction

        data object PreviewPinchFinished : ZoomAction

        data class ControlPressChanged(
            val isPressed: Boolean,
        ) : ZoomAction
    }
}
