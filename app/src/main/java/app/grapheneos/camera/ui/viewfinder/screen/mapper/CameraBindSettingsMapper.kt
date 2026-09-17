package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject

interface CameraBindSettingsMapper {

    fun map(
        state: ViewfinderState,
        target: ViewfinderBindTarget,
    ): CameraBindSettings
}

internal class CameraBindSettingsMapperImpl @Inject constructor() : CameraBindSettingsMapper {

    override fun map(
        state: ViewfinderState,
        target: ViewfinderBindTarget,
    ): CameraBindSettings {
        val settings = state.settings

        return CameraBindSettings(
            mode = state.mode,
            isQrMode = state.isQrMode(),
            isVideoMode = state.isVideoMode(),
            requiresVideoModeOnly = state.requiresVideoModeOnly,
            qrLensFacing = target.qrLensFacing,
            aspectRatio = state.aspectRatio(),
            flashMode = state.flashMode,
            photoQuality = settings.photoQuality,
            videoQuality = state.modeSettings.videoQuality,
            waitForFocusLock = settings.waitForFocusLock,
            enableZsl = settings.enableZsl,
            enableEis = settings.enableEis,
            selectHighestResolution = settings.selectHighestResolution,
            mirrorVideoOnFrontCamera = settings.saveVideoAsPreviewed,
            barcodeFormats = state.barcodeFormats(),
        )
    }
}
