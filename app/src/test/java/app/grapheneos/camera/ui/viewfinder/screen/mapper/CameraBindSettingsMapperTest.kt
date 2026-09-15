package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraBindSettingsMapperTest {

    private val mapper: CameraBindSettingsMapper = CameraBindSettingsMapperImpl()

    @Test
    fun photoMode_carriesWhatWasStoredAndWhereTheLensWasTurned() {
        val state = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
            settings = CameraSettings(
                aspectRatio = AspectRatio.RATIO_16_9,
                photoQuality = PHOTO_QUALITY,
                waitForFocusLock = true,
                enableZsl = true,
                enableEis = true,
                selectHighestResolution = true,
                saveVideoAsPreviewed = true,
            ),
            modeSettings = ModeSettings(videoQuality = Quality.UHD),
            flashMode = ImageCapture.FLASH_MODE_AUTO,
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(rotation = ROTATION, qrLensFacing = null),
        )

        assertEquals(
            CameraBindSettings(
                mode = CameraMode.CAMERA,
                isQrMode = false,
                isVideoMode = false,
                requiresVideoModeOnly = false,
                qrLensFacing = null,
                rotation = ROTATION,
                aspectRatio = AspectRatio.RATIO_16_9,
                flashMode = ImageCapture.FLASH_MODE_AUTO,
                photoQuality = PHOTO_QUALITY,
                videoQuality = Quality.UHD,
                waitForFocusLock = true,
                enableZsl = true,
                enableEis = true,
                selectHighestResolution = true,
                mirrorVideoOnFrontCamera = true,
            ),
            settings,
        )
    }

    @Test
    fun qrMode_bindsFourByThreeOnTheLensTheTargetChose() {
        val state = ViewfinderState(
            mode = CameraMode.QR_SCAN,
            requiresVideoModeOnly = false,
            settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9),
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(
                rotation = ROTATION,
                qrLensFacing = CameraSelector.LENS_FACING_FRONT,
            ),
        )

        assertEquals(true, settings.isQrMode)
        assertEquals(AspectRatio.RATIO_4_3, settings.aspectRatio)
        assertEquals(CameraSelector.LENS_FACING_FRONT, settings.qrLensFacing)
    }

    @Test
    fun videoOnlyEntryPoint_bindsForVideoInWhicheverModeIsSelected() {
        val state = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = true,
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(rotation = ROTATION, qrLensFacing = null),
        )

        assertEquals(true, settings.isVideoMode)
        assertEquals(true, settings.requiresVideoModeOnly)
        assertEquals(AspectRatio.RATIO_16_9, settings.aspectRatio)
    }

    private companion object {
        const val ROTATION = 1
        const val PHOTO_QUALITY = 87
    }
}
