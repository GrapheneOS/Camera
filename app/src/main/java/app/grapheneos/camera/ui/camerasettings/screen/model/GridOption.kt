package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern

internal enum class GridOption(
    val type: GridType,
    @StringRes val label: Int,
    val pattern: CompositionGridPattern?,
) {
    NONE(
        type = GridType.NONE,
        label = R.string.camera_settings_grid_none,
        pattern = null,
    ),
    THREE_BY_THREE(
        type = GridType.THREE_BY_THREE,
        label = R.string.camera_settings_grid_3x3,
        pattern = CompositionGridPattern.Thirds,
    ),
    FOUR_BY_FOUR(
        type = GridType.FOUR_BY_FOUR,
        label = R.string.camera_settings_grid_4x4,
        pattern = CompositionGridPattern.Quarters,
    ),
    GOLDEN_RATIO(
        type = GridType.GOLDEN_RATIO,
        label = R.string.camera_settings_grid_golden_ratio,
        pattern = CompositionGridPattern.GoldenRatio,
    ),
    ;

    companion object {
        fun of(type: GridType): GridOption {
            return entries.first { it.type == type }
        }
    }
}
