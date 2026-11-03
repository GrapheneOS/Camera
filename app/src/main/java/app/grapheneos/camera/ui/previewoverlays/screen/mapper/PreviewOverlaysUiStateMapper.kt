package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import javax.inject.Inject

internal interface PreviewOverlaysUiStateMapper {

    fun map(state: PreviewOverlaysState): PreviewOverlaysUiState
}

internal class PreviewOverlaysUiStateMapperImpl @Inject constructor() :
    PreviewOverlaysUiStateMapper {

    override fun map(state: PreviewOverlaysState): PreviewOverlaysUiState {
        val pattern = when (state.gridType) {
            GridType.THREE_BY_THREE -> CompositionGridPattern.Thirds
            GridType.FOUR_BY_FOUR -> CompositionGridPattern.Quarters
            GridType.GOLDEN_RATIO -> CompositionGridPattern.GoldenRatio
            GridType.NONE -> null
        }

        return PreviewOverlaysUiState(
            gridPattern = pattern ?: CompositionGridPattern.Thirds,
            isGridVisible = state.showsGrid(),
        )
    }
}
