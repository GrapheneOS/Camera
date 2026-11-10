package app.grapheneos.camera.data.camera.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoFrameRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFlashMapperTest {

    private val mapper = CameraFlashMapperImpl()

    @Test
    fun compensatedPhotoExposure_disablesAutoAndOn() {
        val camera = PHOTO_CAMERA.copy(isExposureCompensated = true)
        val unavailable = setOf(FlashMode.AUTO, FlashMode.ON)

        unavailable.forEach { mode ->
            val flash = mapper.map(
                camera = camera,
                requestedMode = mode,
                frameRate = VideoFrameRate.FPS_30,
            )

            assertEquals(mode.name, FlashMode.OFF, flash.selectedMode)
            assertEquals(mode.name, unavailable, flash.unavailableModes)
        }
    }

    @Test
    fun exposureLimits_doNotDisableVideoLightingOrLowLightBoost() {
        val cases = listOf(
            PHOTO_CAMERA.copy(
                isLowLightBoostSupported = true,
                isExposureCompensated = true,
            ) to FlashMode.LOW_LIGHT_BOOST,
            VIDEO_CAMERA.copy(isExposureCompensated = true) to FlashMode.ON,
        )
        cases.forEach { (camera, mode) ->
            val flash = mapper.map(
                camera = camera,
                requestedMode = mode,
                frameRate = VideoFrameRate.FPS_30,
            )

            assertEquals(mode, flash.selectedMode)
        }
    }

    @Test
    fun noExposureCompensation_keepsTheRequestedSupportedMode() {
        val flash = mapper.map(
            camera = PHOTO_CAMERA,
            requestedMode = FlashMode.AUTO,
            frameRate = VideoFrameRate.FPS_30,
        )

        assertEquals(FlashMode.AUTO, flash.selectedMode)
        assertEquals(emptySet<FlashMode>(), flash.unavailableModes)
    }

    @Test
    fun aModeTheCameraDoesNotOffer_isOff() {
        val flash = mapper.map(
            camera = VIDEO_CAMERA,
            requestedMode = FlashMode.AUTO,
            frameRate = VideoFrameRate.FPS_30,
        )

        assertEquals(FlashMode.OFF, flash.selectedMode)
    }

    @Test
    fun lowLightBoost_isLimitedOnlyAtASupportedHighFrameRate() {
        val camera = VIDEO_CAMERA.copy(
            isLowLightBoostSupported = true,
            highFrameRate = HighFrameRateSupport.Supported,
        )
        val limited = mapper.map(
            camera = camera,
            requestedMode = FlashMode.LOW_LIGHT_BOOST,
            frameRate = VideoFrameRate.FPS_60,
        )
        val normal = mapper.map(
            camera = camera,
            requestedMode = FlashMode.LOW_LIGHT_BOOST,
            frameRate = VideoFrameRate.FPS_30,
        )
        val unavailableHighFrameRate = mapper.map(
            camera = camera.copy(highFrameRate = null),
            requestedMode = FlashMode.LOW_LIGHT_BOOST,
            frameRate = VideoFrameRate.FPS_60,
        )

        assertEquals(FlashMode.OFF, limited.selectedMode)
        assertEquals(setOf(FlashMode.LOW_LIGHT_BOOST), limited.unavailableModes)
        assertEquals(FlashMode.LOW_LIGHT_BOOST, normal.selectedMode)
        assertEquals(FlashMode.LOW_LIGHT_BOOST, unavailableHighFrameRate.selectedMode)
    }

    @Test
    fun selfIllumination_isSelectedOnlyOnTheFrontLens() {
        val front = mapper.map(
            camera = PHOTO_CAMERA.copy(lensFacing = LensFacing.FRONT),
            requestedMode = FlashMode.SELF_ILLUMINATION,
            frameRate = VideoFrameRate.FPS_30,
        )
        val back = mapper.map(
            camera = PHOTO_CAMERA,
            requestedMode = FlashMode.SELF_ILLUMINATION,
            frameRate = VideoFrameRate.FPS_30,
        )

        assertTrue(front.isSelfIlluminating())
        assertFalse(back.isSelfIlluminating())
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
        )
    }
}
