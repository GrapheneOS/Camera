package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import app.grapheneos.camera.data.core.model.CameraMode

data class ModeTabsUiModel(
    val visible: Boolean = false,
    val availableModes: Set<CameraMode> = emptySet(),
    val selectedMode: CameraMode = CameraMode.CAMERA,
    val selectionId: Int = 0,
)
