package app.grapheneos.camera.ui.previewoverlays.screen.model.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class ZoomUiModel(
    val ratio: Float,
    val range: ClosedFloatingPointRange<Float>,
    val presets: ImmutableList<Float>,
    val isCurrentValueShown: Boolean = true,
    val isExpanded: Boolean = false,
    val isPinching: Boolean = false,
)
