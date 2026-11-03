package app.grapheneos.camera.ui.statusbar.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class StatusBarUiState(
    val items: ImmutableList<StatusItemUiModel> = persistentListOf(),
    val isSelfIlluminating: Boolean = false,
)
