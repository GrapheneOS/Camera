package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickSettingsUiStateMapperTest {

    private val mapper: QuickSettingsUiStateMapper = QuickSettingsUiStateMapperImpl()

    @Test
    fun flash_followsTheModeItWasSetTo() {
        val on = map(modeSettings = ModeSettings(flashMode = FlashMode.ON))
        val auto = map(modeSettings = ModeSettings(flashMode = FlashMode.AUTO))
        val off = map(modeSettings = ModeSettings(flashMode = FlashMode.OFF))

        assertEquals(R.drawable.flash_on_circle, on.flashIcon)
        assertEquals(R.string.flash_on, on.flashDescription)

        assertEquals(R.drawable.flash_auto_circle, auto.flashIcon)
        assertEquals(R.string.flash_auto, auto.flashDescription)

        assertEquals(R.drawable.flash_off_circle, off.flashIcon)
        assertEquals(R.string.flash_off, off.flashDescription)
    }

    @Test
    fun flash_unavailable_readsAsOffWhateverWasStored() {
        val state = map(
            modeSettings = ModeSettings(flashMode = FlashMode.ON),
            camera = PHOTO_CAMERA.copy(hasFlashUnit = false),
        )

        assertEquals(R.drawable.flash_off_circle, state.flashIcon)
        assertEquals(R.string.flash_off, state.flashDescription)
    }

    @Test
    fun videoRows_areOnlyShownInVideoMode() {
        val video = map(camera = VIDEO_CAMERA)
        val photo = map(camera = PHOTO_CAMERA)

        assertTrue(video.includeAudioSettingVisible)
        assertTrue(video.videoQualitySettingVisible)
        assertFalse(video.timerSettingVisible)

        assertFalse(photo.includeAudioSettingVisible)
        assertFalse(photo.videoQualitySettingVisible)
        assertTrue(photo.timerSettingVisible)
    }

    @Test
    fun selfIlluminationRow_isForTheFrontLensOnly() {
        val front = PHOTO_CAMERA.copy(lensFacing = LensFacing.FRONT)

        assertTrue(map(camera = front).selfIlluminationSettingVisible)
        assertFalse(map(camera = PHOTO_CAMERA).selfIlluminationSettingVisible)
    }

    @Test
    fun toggles_readTheValuesTheyStandFor() {
        val state = map(
            settings = CameraSettings(includeAudio = true),
            modeSettings = ModeSettings(selfIllumination = true),
        )

        assertTrue(state.includeAudio)
        assertTrue(state.selfIllumination)
    }

    @Test
    fun selfIlluminationToggle_isTheStoredValueNotTheEffectiveOne() {
        val state = map(modeSettings = ModeSettings(selfIllumination = true))

        assertTrue(state.selfIllumination)
        assertFalse(state.selfIlluminationSettingVisible)
    }

    @Test
    fun aspectRatioToggle_announcesTheRatioItIsOn() {
        val wide = map(settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9))
        val narrow = map(settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3))

        assertTrue(wide.is16by9)
        assertEquals(R.string.aspect_ratio_16_9, wide.aspectRatioDescription)

        assertFalse(narrow.is16by9)
        assertEquals(R.string.aspect_ratio_4_3, narrow.aspectRatioDescription)
    }

    @Test
    fun aspectRatio_inVideoMode_isFixedAtWideWhateverWasStored() {
        val state = map(
            settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3),
            camera = VIDEO_CAMERA,
        )

        assertTrue(state.is16by9)
        assertTrue(state.aspectRatioFixed)
    }

    @Test
    fun videoQuality_theStoredOne_isSelectedAmongTheOffered() {
        val state = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.FHD),
            camera = VIDEO_CAMERA,
        )

        assertEquals(OFFERED_QUALITIES, state.videoQualities)
        assertEquals(1, state.videoQualityPosition)
    }

    @Test
    fun videoQuality_leftToTheDevice_selectsTheHighestOffered() {
        val state = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.HIGHEST),
            camera = VIDEO_CAMERA,
        )

        assertEquals(0, state.videoQualityPosition)
    }

    @Test
    fun videoQuality_notOfferedByThisCamera_selectsNothing() {
        val state = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.SD),
            camera = VIDEO_CAMERA,
        )

        assertNull(state.videoQualityPosition)
    }

    @Test
    fun videoQuality_beforeACameraIsBound_selectsNothing() {
        val state = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.HIGHEST),
            camera = null,
        )

        assertNull(state.videoQualityPosition)
    }

    @Test
    fun torch_followsTheCameraAndTheState() {
        val state = map(isTorchOn = true)

        assertTrue(state.torchAvailable)
        assertTrue(state.torchOn)
    }

    private fun map(
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        camera: BoundCamera? = PHOTO_CAMERA,
        isTorchOn: Boolean = false,
    ): QuickSettingsUiState {
        return mapper.map(
            QuickSettingsState(
                settings = settings,
                modeSettings = modeSettings,
                boundCamera = camera,
                isTorchOn = isTorchOn,
            ),
        )
    }

    private companion object {
        val OFFERED_QUALITIES = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD)

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
            videoQualities = OFFERED_QUALITIES,
        )
    }
}
