package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.ui.previewoverlays.screen.model.input.ZoomDirection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState

internal sealed interface PreviewOverlaysAction {

    sealed interface LifecycleAction : PreviewOverlaysAction {

        data object ScreenResumed : LifecycleAction

        data object ScreenPaused : LifecycleAction
    }

    sealed interface FocusAction : PreviewOverlaysAction {

        data object FocusKeyPressed : FocusAction

        data object PreviewTapped : FocusAction

        data object PreviewLongPressed : FocusAction

        data object PreviewGestureStarted : FocusAction

        data class PreviewPressed(
            val x: Float,
            val y: Float,
            val hitRadius: Float,
        ) : FocusAction
    }

    sealed interface PreviewAction : PreviewOverlaysAction {

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

    sealed interface ExposureAction : PreviewOverlaysAction {

        data object ResetClicked : ExposureAction

        data class ValueChanged(
            val value: Float,
        ) : ExposureAction

        data class PressChanged(
            val isPressed: Boolean,
        ) : ExposureAction
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
