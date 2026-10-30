package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Immutable
internal data class QuickChoiceUiModel<T>(
    val selected: T,
    val options: ImmutableList<T>,
    val unavailable: ImmutableSet<T> = persistentSetOf(),
)
