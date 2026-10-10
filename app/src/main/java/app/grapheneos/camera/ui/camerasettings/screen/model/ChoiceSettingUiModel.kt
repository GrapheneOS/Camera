package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class ChoiceSettingUiModel<T>(
    val selected: T,
    val options: ImmutableList<T>,
    val isChanged: Boolean = false,
)
