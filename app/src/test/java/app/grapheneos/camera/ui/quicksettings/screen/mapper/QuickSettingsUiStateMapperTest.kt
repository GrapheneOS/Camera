package app.grapheneos.camera.ui.quicksettings.screen.mapper

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
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickSettingsUiStateMapperTest {

    private val mapper: QuickSettingsUiStateMapper = QuickSettingsUiStateMapperImpl()

    @Test
    fun beforeACameraIsBound_thereIsNothingToSet() {
        assertEquals(QuickSettingsUiState(), map(camera = null))
    }

    @Test
    fun photoMode_offersTheTimerAndTheAspectRatioButNotTheVideoSettings() {
        val state = map(camera = PHOTO_CAMERA)

        assertFalse(state.isVideoMode)
        assertNotNull(state.selfTimer)
        assertNotNull(state.aspectRatio)
        assertNull(state.videoQuality)
        assertNull(state.audio)
    }

    @Test
    fun videoMode_offersTheVideoSettingsButNotTheTimerOrTheAspectRatio() {
        val state = map(camera = VIDEO_CAMERA)

        assertTrue(state.isVideoMode)
        assertNull(state.selfTimer)
        assertNull(state.aspectRatio)
        assertNotNull(state.videoQuality)
        assertNotNull(state.audio)
    }

    @Test
    fun flash_selectsTheStoredModeAmongAllModes() {
        val flash = map(modeSettings = ModeSettings(flashMode = FlashMode.AUTO)).flash

        assertEquals(FlashMode.AUTO, flash?.selected)
        assertEquals(FlashMode.entries, flash?.options)
    }

    @Test
    fun flash_inAVideoOnlyEntryPoint_cannotBeChanged() {
        assertEquals(false, map(requiresVideoModeOnly = true).flash?.enabled)
    }

    @Test
    fun withoutAFlashUnit_neitherTheFlashNorTheTorchIsOffered() {
        val state = map(camera = PHOTO_CAMERA.copy(hasFlashUnit = false))

        assertNull(state.flash)
        assertNull(state.torch)
    }

    @Test
    fun torch_followsTheState() {
        assertEquals(true, map(isTorchOn = true).torch)
    }

    @Test
    fun selfIllumination_isOfferedForTheFrontLensOnly() {
        val modeSettings = ModeSettings(selfIllumination = true)

        val front = map(modeSettings = modeSettings, camera = FRONT_PHOTO_CAMERA)
        val back = map(modeSettings = modeSettings)

        assertEquals(true, front.selfIllumination)
        assertTrue(front.isSelfIlluminating)
        assertNull(back.selfIllumination)
        assertFalse(back.isSelfIlluminating)
    }

    @Test
    fun selfTimer_keepsTheStoredDurationEvenWithoutAMatchingOption() {
        val selfTimer = map(settings = CameraSettings(selfTimerDuration = 8.seconds)).selfTimer

        assertEquals(8.seconds, selfTimer?.selected)
        assertFalse(selfTimer?.options.orEmpty().contains(8.seconds))
    }

    @Test
    fun aspectRatio_selectsTheStoredRatio() {
        val settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9)

        val aspectRatio = map(settings = settings).aspectRatio

        assertEquals(AspectRatio.RATIO_16_9, aspectRatio?.selected)
    }

    @Test
    fun videoQuality_selectsTheStoredQualityAmongTheOffered() {
        val videoQuality = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.FHD),
            camera = VIDEO_CAMERA,
        ).videoQuality

        assertEquals(VideoQuality.FHD, videoQuality?.selected)
        assertEquals(OFFERED_QUALITIES, videoQuality?.options)
    }

    @Test
    fun videoQuality_leftToTheDevice_selectsTheHighestOffered() {
        val videoQuality = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.HIGHEST),
            camera = VIDEO_CAMERA,
        ).videoQuality

        assertEquals(VideoQuality.UHD, videoQuality?.selected)
    }

    @Test
    fun audio_followsTheStoredSetting() {
        val state = map(
            settings = CameraSettings(includeAudio = false),
            camera = VIDEO_CAMERA,
        )

        assertEquals(false, state.audio)
    }

    private fun map(
        requiresVideoModeOnly: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        camera: BoundCamera? = PHOTO_CAMERA,
        isTorchOn: Boolean = false,
    ): QuickSettingsUiState {
        return mapper.map(
            QuickSettingsState(
                requiresVideoModeOnly = requiresVideoModeOnly,
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

        val FRONT_PHOTO_CAMERA = PHOTO_CAMERA.copy(lensFacing = LensFacing.FRONT)

        val VIDEO_CAMERA = PHOTO_CAMERA.copy(
            mode = CameraMode.VIDEO,
            isVideoMode = true,
            videoQualities = OFFERED_QUALITIES,
        )
    }
}
