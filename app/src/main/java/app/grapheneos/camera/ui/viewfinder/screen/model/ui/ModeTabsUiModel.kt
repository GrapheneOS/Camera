package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.CameraMode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class ModeTabsUiModel(
    val visible: Boolean = false,
    val availableModes: ImmutableSet<CameraMode> = persistentSetOf(),
    val selectedMode: CameraMode = CameraMode.CAMERA,
    val selectionId: Int = 0,
)
