package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class QuickChoiceUiModel<T>(
    val selected: T,
    val options: ImmutableList<T>,
)
