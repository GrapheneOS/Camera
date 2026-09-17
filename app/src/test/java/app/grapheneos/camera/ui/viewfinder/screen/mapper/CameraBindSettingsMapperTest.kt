package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
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
            modeSettings = ModeSettings(videoQuality = VideoQuality.UHD),
            flashMode = FlashMode.AUTO,
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
                flashMode = FlashMode.AUTO,
                photoQuality = PHOTO_QUALITY,
                videoQuality = VideoQuality.UHD,
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
                qrLensFacing = LensFacing.FRONT,
            ),
        )

        assertEquals(true, settings.isQrMode)
        assertEquals(AspectRatio.RATIO_4_3, settings.aspectRatio)
        assertEquals(LensFacing.FRONT, settings.qrLensFacing)
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
