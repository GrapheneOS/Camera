package app.grapheneos.camera.ui.previewoverlays.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import kotlin.time.Duration

@Immutable
internal data class PreviewContentState(
    val mode: CameraMode = CameraMode.CAMERA,
    val lensFacing: LensFacing = LensFacing.BACK,
    val isPreviewStreaming: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isTransitionFrameVisible: Boolean = false,
    val isCaptureResultVisible: Boolean = false,
    val selfTimerRemaining: Duration? = null,
)
