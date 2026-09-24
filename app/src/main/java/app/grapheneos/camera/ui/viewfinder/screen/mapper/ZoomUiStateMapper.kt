package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import javax.inject.Inject

interface ZoomUiStateMapper {
    fun map(state: ViewfinderState): ZoomUiState
}

internal class ZoomUiStateMapperImpl @Inject constructor() : ZoomUiStateMapper {

    override fun map(state: ViewfinderState): ZoomUiState {
        return when (val zoom = state.session.zoom) {
            null -> ZoomUiState()
            else -> ZoomUiState(
                zoomRatio = zoom.zoomRatio,
                linearZoom = zoom.linearZoom,
            )
        }
    }
}
