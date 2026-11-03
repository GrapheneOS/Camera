package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.compose.runtime.Immutable

@Immutable
data class ZoomUiState(
    val zoomRatio: Float = 1f,
    val linearZoom: Float = 0f,
    val panelVisible: Boolean = false,
)
