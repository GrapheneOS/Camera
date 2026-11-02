package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.CameraMode

data class ModeTabsUiState(
    val visible: Boolean = false,
    val availableModes: Set<CameraMode> = emptySet(),
)
