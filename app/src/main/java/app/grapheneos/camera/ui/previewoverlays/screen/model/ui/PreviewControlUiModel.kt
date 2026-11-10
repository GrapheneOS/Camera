package app.grapheneos.camera.ui.previewoverlays.screen.model.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal sealed interface PreviewControlUiModel {

    @Immutable
    data class Zoom(
        val ratio: Float,
        val range: ClosedFloatingPointRange<Float>,
        val presets: ImmutableList<Float>,
        val isCurrentValueShown: Boolean = true,
        val isExpanded: Boolean = false,
        val isPinching: Boolean = false,
    ) : PreviewControlUiModel

    @Immutable
    data class Exposure(
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val majorTickInterval: Int,
        val isResetVisible: Boolean = false,
    ) : PreviewControlUiModel
}
