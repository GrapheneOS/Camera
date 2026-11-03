package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.compose.runtime.Immutable

@Immutable
data class LevelUiState(
    val visible: Boolean = false,
    val tiltDegrees: Int = 0,
    val horizonDegrees: Int = 0,
)
