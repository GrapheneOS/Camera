package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class CaptureModeUiModel(
    val selected: CaptureModeOption = CaptureModeOption.SPEED,
    val options: ImmutableList<CaptureModeOption> = persistentListOf(),
    val isChanged: Boolean = false,
)
