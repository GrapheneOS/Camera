package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable

@Immutable
data class QrUiModel(
    val overlayVisible: Boolean = false,
    val scanTogglesVisible: Boolean = false,
)
