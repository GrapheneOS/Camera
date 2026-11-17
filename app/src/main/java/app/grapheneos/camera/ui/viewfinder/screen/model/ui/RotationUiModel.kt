package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable

@Immutable
data class RotationUiModel(
    val iconDegrees: Float = 0f,
    val levelFrameDegrees: Float = 0f,
)
