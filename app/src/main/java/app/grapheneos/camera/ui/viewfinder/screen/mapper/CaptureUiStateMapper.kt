package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.CameraSelector
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import javax.inject.Inject

interface CaptureUiStateMapper {

    fun map(
        requireLocation: Boolean,
        settings: CameraSettings,
        modeSettings: ModeSettings,
        session: ViewfinderSessionState,
    ): CaptureUiState
}

internal class CaptureUiStateMapperImpl @Inject constructor() : CaptureUiStateMapper {

    override fun map(
        requireLocation: Boolean,
        settings: CameraSettings,
        modeSettings: ModeSettings,
        session: ViewfinderSessionState,
    ): CaptureUiState {
        return CaptureUiState(
            canTakePicture = session.canTakePicture,
            saveImageAsPreviewed = settings.saveImageAsPreviewed,
            removeExifAfterCapture = settings.removeExifAfterCapture,
            geoTagging = requireLocation,
            selfIlluminate = modeSettings.selfIllumination &&
                session.lensFacing == CameraSelector.LENS_FACING_FRONT,
            includeAudio = settings.includeAudio,
            cameraSounds = settings.enableCameraSounds,
        )
    }
}
