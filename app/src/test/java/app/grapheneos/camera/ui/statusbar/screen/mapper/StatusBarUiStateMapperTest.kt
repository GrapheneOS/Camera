package app.grapheneos.camera.ui.statusbar.screen.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarUiState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBarUiStateMapperTest {

    private val mapper: StatusBarUiStateMapper = StatusBarUiStateMapperImpl()

    @Test
    fun beforeACameraIsBound_showsNothing() {
        assertEquals(StatusBarUiState(), map(camera = null))
    }

    @Test
    fun inPhotoModeAtTheDefaults_showsNothing() {
        assertEquals(emptyList<StatusItemUiModel>(), map().items)
    }

    @Test
    fun showsTheFlashAndTheTimerOnlyWhenTheyAreNotOff_inTheOrderOfTheSheet() {
        val state = map(
            settings = CameraSettings(selfTimerDuration = 3.seconds),
            modeSettings = ModeSettings(flashMode = FlashMode.AUTO),
        )

        assertEquals(
            listOf(
                StatusItemUiModel.Flash(mode = FlashMode.AUTO),
                StatusItemUiModel.SelfTimer(duration = 3.seconds),
            ),
            state.items,
        )
    }

    @Test
    fun withoutAFlashUnit_neverShowsTheFlash() {
        val state = map(
            modeSettings = ModeSettings(flashMode = FlashMode.ON),
            camera = PHOTO_CAMERA.copy(hasFlashUnit = false),
        )

        assertEquals(emptyList<StatusItemUiModel>(), state.items)
    }

    @Test
    fun onTheFrontLens_showsTheSelfIlluminationWhenItIsOn() {
        val state = map(
            modeSettings = ModeSettings(selfIllumination = true),
            camera = PHOTO_CAMERA.copy(lensFacing = LensFacing.FRONT),
        )

        assertTrue(state.isSelfIlluminating)
        assertEquals(listOf(StatusItemUiModel.SelfIllumination), state.items)
    }

    @Test
    fun inVideoMode_alwaysShowsTheRecordedResolutionAfterTheLight() {
        val atTheDefaults = map(camera = VIDEO_CAMERA)
        val withTheLight = map(
            modeSettings = ModeSettings(
                flashMode = FlashMode.ON,
                videoQuality = VideoQuality.FHD,
            ),
            camera = VIDEO_CAMERA,
        )

        assertEquals(
            listOf(StatusItemUiModel.Resolution(quality = VideoQuality.UHD)),
            atTheDefaults.items,
        )
        assertEquals(
            listOf(
                StatusItemUiModel.VideoLight,
                StatusItemUiModel.Resolution(quality = VideoQuality.FHD),
            ),
            withTheLight.items,
        )
    }

    private fun map(
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        camera: BoundCamera? = PHOTO_CAMERA,
    ): StatusBarUiState {
        return mapper.map(
            StatusBarState(
                settings = settings,
                modeSettings = modeSettings,
                boundCamera = camera,
            ),
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
