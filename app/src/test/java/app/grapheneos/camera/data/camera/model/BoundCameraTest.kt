package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundCameraTest {

    @Test
    fun modeSlot_isTheModeAndTheLensItIsBoundTo() {
        assertEquals(
            ModeSlot(mode = CameraMode.VIDEO, lensFacing = LensFacing.FRONT),
            VIDEO_CAMERA.copy(lensFacing = LensFacing.FRONT).modeSlot(),
        )
    }

    @Test
    fun flashModes_offerAutoForPhotosOnly() {
        assertEquals(listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON), PHOTO_CAMERA.flashModes())
        assertEquals(listOf(FlashMode.OFF, FlashMode.ON), VIDEO_CAMERA.flashModes())
    }

    @Test
    fun flashModes_withoutAFlashUnit_leaveOnlyOff() {
        assertEquals(listOf(FlashMode.OFF), PHOTO_CAMERA.copy(hasFlashUnit = false).flashModes())
    }

    @Test
    fun flashModes_endWithLowLightBoostWhereTheCameraSupportsIt() {
        assertEquals(
            listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON, FlashMode.LOW_LIGHT_BOOST),
            PHOTO_CAMERA.copy(isLowLightBoostSupported = true).flashModes(),
        )
        assertEquals(
            listOf(FlashMode.OFF, FlashMode.LOW_LIGHT_BOOST),
            PHOTO_CAMERA.copy(hasFlashUnit = false, isLowLightBoostSupported = true).flashModes(),
        )
    }

    private companion object {
        val PHOTO_CAMERA = BoundCamera(
            mode = CameraMode.CAMERA,
            isVideoMode = false,
            lensFacing = LensFacing.BACK,
            hasFlashUnit = true,
            videoQualities = emptyList(),
        )

        val VIDEO_CAMERA = PHOTO_CAMERA.copy(
            mode = CameraMode.VIDEO,
            isVideoMode = true,
            videoQualities = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD),
        )
    }
}
