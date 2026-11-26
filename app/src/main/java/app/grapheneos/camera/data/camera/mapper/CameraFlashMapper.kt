package app.grapheneos.camera.data.camera.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.CameraFlash
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import javax.inject.Inject

interface CameraFlashMapper {

    fun map(
        camera: BoundCamera,
        requestedMode: FlashMode,
        frameRate: VideoFrameRate,
    ): CameraFlash
}

internal class CameraFlashMapperImpl @Inject constructor() : CameraFlashMapper {

    override fun map(
        camera: BoundCamera,
        requestedMode: FlashMode,
        frameRate: VideoFrameRate,
    ): CameraFlash {
        val unavailableModes = buildSet {
            if (limitsLowLightBoost(camera = camera, frameRate = frameRate)) {
                add(FlashMode.LOW_LIGHT_BOOST)
            }
        }
        val isSelectable = requestedMode in camera.flashModes() &&
            requestedMode !in unavailableModes

        return CameraFlash(
            selectedMode = when {
                isSelectable -> requestedMode
                else -> FlashMode.OFF
            },
            unavailableModes = unavailableModes,
        )
    }

    // CameraX turns low light boost off above 30 fps.
    private fun limitsLowLightBoost(
        camera: BoundCamera,
        frameRate: VideoFrameRate,
    ): Boolean {
        return camera.isLowLightBoostSupported && camera.isVideoMode &&
            frameRate == VideoFrameRate.FPS_60 &&
            camera.highFrameRate == HighFrameRateSupport.Supported
    }
}
