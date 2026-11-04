package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.PhotoBindSettings
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.JpegQuality
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                captureMode = CaptureMode.QUALITY,
                enableEis = true,
                selectHighestResolution = true,
                ultraHdr = true,
                keepSelfiesAsSeen = true,
            ),
            modeSettings = ModeSettings(
                videoQuality = VideoQuality.UHD,
                videoFrameRate = VideoFrameRate.FPS_60,
            ),
            flashMode = FlashMode.AUTO,
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(qrLensFacing = null),
        )

        assertEquals(
            CameraBindSettings(
                mode = CameraMode.CAMERA,
                aspectRatio = AspectRatio.RATIO_16_9,
                photo = PhotoBindSettings(
                    flashMode = FlashMode.AUTO,
                    quality = JpegQuality(percent = PHOTO_QUALITY),
                    captureMode = CaptureMode.QUALITY,
                    selectHighestResolution = true,
                    ultraHdr = true,
                ),
                video = null,
                qr = null,
            ),
            settings,
        )
    }

    @Test
    fun videoMode_bindsTheVideoSettingsAndPlainSnapshots() {
        val state = ViewfinderState(
            mode = CameraMode.VIDEO,
            requiresVideoModeOnly = false,
            settings = CameraSettings(
                enableEis = true,
                hdrVideo = true,
                ultraHdr = true,
                keepSelfiesAsSeen = true,
            ),
            modeSettings = ModeSettings(
                videoQuality = VideoQuality.UHD,
                videoFrameRate = VideoFrameRate.FPS_60,
            ),
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(qrLensFacing = null),
        )

        assertEquals(
            VideoBindSettings(
                quality = VideoQuality.UHD,
                frameRate = VideoFrameRate.FPS_60,
                stabilization = true,
                hdr = true,
                mirrorOnFrontCamera = true,
            ),
            settings.video,
        )
        assertEquals(false, settings.photo?.ultraHdr)
        assertNull(settings.qr)
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
            target = ViewfinderBindTarget(qrLensFacing = LensFacing.FRONT),
        )

        assertEquals(LensFacing.FRONT, settings.qr?.lensFacing)
        assertEquals(AspectRatio.RATIO_4_3, settings.aspectRatio)
        assertNull(settings.photo)
        assertNull(settings.video)
    }

    @Test
    fun videoOnlyEntryPoint_bindsForVideoInWhicheverModeIsSelected() {
        val state = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = true,
        )

        val settings = mapper.map(
            state = state,
            target = ViewfinderBindTarget(qrLensFacing = null),
        )

        assertNotNull(settings.video)
        assertNull(settings.photo)
        assertEquals(AspectRatio.RATIO_16_9, settings.aspectRatio)
    }

    private companion object {
        const val PHOTO_QUALITY = 87
    }
}
