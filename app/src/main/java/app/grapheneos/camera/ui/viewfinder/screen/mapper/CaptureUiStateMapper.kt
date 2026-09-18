package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject

interface CaptureUiStateMapper {
    fun map(state: ViewfinderState): CaptureUiState
}

internal class CaptureUiStateMapperImpl @Inject constructor() : CaptureUiStateMapper {

    override fun map(state: ViewfinderState): CaptureUiState {
        return CaptureUiState(
            canTakePicture = state.session.canTakePicture,
            saveImageAsPreviewed = state.settings.saveImageAsPreviewed,
            removeExifAfterCapture = state.settings.removeExifAfterCapture,
            geoTagging = state.requireLocation,
            includeAudio = state.settings.includeAudio,
            cameraSounds = state.settings.enableCameraSounds,
        )
    }
}
