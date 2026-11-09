package app.grapheneos.camera.ui.previewoverlays.screen.model

import app.grapheneos.camera.data.core.model.DeviceOrientation
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
}
