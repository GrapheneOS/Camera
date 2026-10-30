package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.PhotoBindSettings
import app.grapheneos.camera.data.camera.model.QrBindSettings
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.core.model.JpegQuality
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
            aspectRatio = state.aspectRatio(),
            photo = PhotoBindSettings(
                flashMode = state.flashMode,
                quality = JpegQuality(percent = settings.photoQuality),
                captureMode = settings.captureMode,
                selectHighestResolution = settings.selectHighestResolution,
                ultraHdr = settings.ultraHdr && !state.isVideoMode(),
            ).takeIf { !state.isQrMode() && !state.requiresVideoModeOnly },
            video = VideoBindSettings(
                quality = state.modeSettings.videoQuality,
                frameRate = state.modeSettings.videoFrameRate,
                stabilization = settings.enableEis,
                hdr = settings.hdrVideo,
                mirrorOnFrontCamera = settings.keepSelfiesAsSeen,
            ).takeIf { !state.isQrMode() && state.isVideoMode() },
            qr = QrBindSettings(
                lensFacing = target.qrLensFacing,
                barcodeFormats = state.barcodeFormats(),
            ).takeIf { state.isQrMode() },
        )
    }
}
