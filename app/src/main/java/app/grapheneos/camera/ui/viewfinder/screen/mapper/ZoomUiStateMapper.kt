package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import javax.inject.Inject

interface ZoomUiStateMapper {
    fun map(state: ViewfinderState): ZoomUiState
}

internal class ZoomUiStateMapperImpl @Inject constructor() : ZoomUiStateMapper {

    override fun map(state: ViewfinderState): ZoomUiState {
        val zoom = state.session.zoom
        val panelVisible = state.previewControls.isZoomPanelShown

        return when (zoom) {
            null -> ZoomUiState(panelVisible = panelVisible)
            else -> ZoomUiState(
                zoomRatio = zoom.zoomRatio,
                linearZoom = zoom.linearZoom,
                panelVisible = panelVisible,
            )
        }
    }
}
