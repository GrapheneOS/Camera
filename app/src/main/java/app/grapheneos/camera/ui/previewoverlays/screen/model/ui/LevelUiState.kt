package app.grapheneos.camera.ui.previewoverlays.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode

@Immutable
internal data class LevelUiState(
    val visible: Boolean = false,
    val mode: LevelIndicatorMode = LevelIndicatorMode.Horizon,
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rotationDegrees: Float = 0f,
)
