package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.AspectRatio

@Immutable
data class PreviewUiModel(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val sensorOrientationDegrees: Int? = null,
    val isStreaming: Boolean = false,
    val lastFrameVisible: Boolean = false,
    val blursLastFrame: Boolean = true,
)
