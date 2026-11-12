package app.grapheneos.camera.ui.quicksettings.screen.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickChoiceUiModel
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsDialog
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
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
    fun flash_offersTheCameraModesAndSelectsTheAppliedOne() {
        val flash = map(
            modeSettings = ModeSettings(flashMode = FlashMode.AUTO),
            camera = VIDEO_CAMERA,
        ).flash

        assertEquals(VIDEO_CAMERA.flashModes(), flash?.options)
        assertEquals(FlashMode.OFF, flash?.selected)
    }

    @Test
    fun withoutAFlashUnitOrLowLightBoost_theFlashIsNotOffered() {
        val noFlashUnit = PHOTO_CAMERA.copy(hasFlashUnit = false)

        assertNull(map(camera = noFlashUnit).flash)
        assertNull(map(camera = noFlashUnit.copy(isVideoMode = true)).flash)
    }

    @Test
    fun lowLightBoost_atAHighFrameRate_isUnavailableAndShowsTheFlashOff() {
        val state = map(
            modeSettings = ModeSettings(
                flashMode = FlashMode.LOW_LIGHT_BOOST,
                videoFrameRate = VideoFrameRate.FPS_60,
            ),
            camera = BOOSTED_VIDEO_CAMERA.copy(highFrameRate = HighFrameRateSupport.Supported),
        )

        assertEquals(FlashMode.OFF, state.flash?.selected)
        assertEquals(setOf(FlashMode.LOW_LIGHT_BOOST), state.flash?.unavailable)
        assertEquals(OptionLimit.LowLightBoostAtHighFrameRate, state.flash?.limit)
    }

    @Test
    fun shownLimit_isExplainedInADialog() {
        val state = mapper.map(
            QuickSettingsState(
                boundCamera = BOOSTED_VIDEO_CAMERA,
                shownLimit = OptionLimit.LowLightBoostAtHighFrameRate,
            ),
        )

        assertEquals(
            QuickSettingsDialog.OptionUnavailable(OptionLimit.LowLightBoostAtHighFrameRate),
            state.dialog,
        )
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
        val selfTimer = requireNotNull(
            map(settings = CameraSettings(selfTimerDuration = 8.seconds)).selfTimer,
        )

        assertEquals(8.seconds, selfTimer.selected)
        assertFalse(selfTimer.options.contains(8.seconds))
    }

    @Test
    fun aspectRatio_selectsTheStoredRatio() {
        val settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9)

        val aspectRatio = map(settings = settings).aspectRatio

        assertEquals(AspectRatio.RATIO_16_9, aspectRatio?.selected)
    }

    @Test
    fun videoQuality_selectsTheStoredQualityAmongTheOfferedFromLowestToHighest() {
        val videoQuality = map(
            modeSettings = ModeSettings(videoQuality = VideoQuality.FHD),
            camera = VIDEO_CAMERA,
        ).videoQuality

        assertEquals(VideoQuality.FHD, videoQuality?.selected)
        assertEquals(OFFERED_QUALITIES.reversed(), videoQuality?.options)
    }

    @Test
    fun videoFrameRate_isOfferedOnlyWhereTheCameraWasCheckedForAHighFrameRate() {
        assertNull(map(camera = VIDEO_CAMERA).videoFrameRate)
    }

    @Test
    fun videoFrameRate_whereTheHighRateFits_selectsTheStoredRate() {
        val state = map(
            modeSettings = ModeSettings(videoFrameRate = VideoFrameRate.FPS_60),
            camera = VIDEO_CAMERA.copy(highFrameRate = HighFrameRateSupport.Supported),
        )

        assertEquals(
            QuickChoiceUiModel(
                selected = VideoFrameRate.FPS_60,
                options = VideoFrameRate.entries.toImmutableList(),
            ),
            state.videoFrameRate,
        )
    }

    @Test
    fun videoFrameRate_whereTheHighRateDoesNotFit_showsTheRecordedRateAndWhy() {
        val state = map(
            modeSettings = ModeSettings(videoFrameRate = VideoFrameRate.FPS_60),
            camera = VIDEO_CAMERA.copy(
                highFrameRate = HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.HDR),
            ),
        )

        assertEquals(
            QuickChoiceUiModel(
                selected = VideoFrameRate.FPS_30,
                options = VideoFrameRate.entries.toImmutableList(),
                unavailable = persistentSetOf(VideoFrameRate.FPS_60),
                limit = OptionLimit.HighFrameRate(limit = HighFrameRateLimit.HDR),
            ),
            state.videoFrameRate,
        )
    }

    @Test
    fun whileRecording_offersOnlyTheLights() {
        val state = map(
            camera = BOOSTED_VIDEO_CAMERA.copy(
                lensFacing = LensFacing.FRONT,
                highFrameRate = HighFrameRateSupport.Supported,
            ),
            isRecording = true,
        )

        assertNotNull(state.flash)
        assertNotNull(state.selfIllumination)
        assertNull(state.videoQuality)
        assertNull(state.videoFrameRate)
        assertNull(state.audio)
        assertFalse(map(camera = null, isRecording = true).moreButtonVisible)
        assertFalse(state.moreButtonVisible)
        assertTrue(map(camera = VIDEO_CAMERA).moreButtonVisible)
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
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        camera: BoundCamera? = PHOTO_CAMERA,
        isRecording: Boolean = false,
    ): QuickSettingsUiState {
        return mapper.map(
            QuickSettingsState(
                settings = settings,
                modeSettings = modeSettings,
                boundCamera = camera,
                isRecording = isRecording,
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

        val BOOSTED_VIDEO_CAMERA = VIDEO_CAMERA.copy(isLowLightBoostSupported = true)
    }
}
