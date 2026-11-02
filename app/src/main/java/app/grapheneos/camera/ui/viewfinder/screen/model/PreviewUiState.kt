package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.settings.model.GridType

data class PreviewUiState(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val sensorOrientationDegrees: Int? = null,
    val gridType: GridType = GridType.NONE,
)
