package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import kotlin.time.Duration

internal data class PreviewContentState(
    val mode: CameraMode = CameraMode.CAMERA,
    val lensFacing: LensFacing = LensFacing.BACK,
    val isPreviewStreaming: Boolean = false,
    val isTransitionFrameVisible: Boolean = false,
    val isCaptureResultVisible: Boolean = false,
    val selfTimerRemaining: Duration? = null,
)
