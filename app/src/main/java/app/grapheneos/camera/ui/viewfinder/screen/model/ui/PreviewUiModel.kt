package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.settings.model.GridType

@Immutable
data class PreviewUiModel(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val sensorOrientationDegrees: Int? = null,
    val gridType: GridType = GridType.NONE,
    val isStreaming: Boolean = false,
    val lastFrameVisible: Boolean = false,
    val blursLastFrame: Boolean = true,
)
