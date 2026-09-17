package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.SettingsSheetUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSheetUiStateMapperTest {

    private val mapper: SettingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl()

    private fun map(
        mode: CameraMode = CameraMode.CAMERA,
        requiresVideoModeOnly: Boolean = false,
        flashMode: FlashMode = FlashMode.OFF,
        requireLocation: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
    ): SettingsSheetUiState {
        return mapper.map(
            ViewfinderState(
                mode = mode,
                requiresVideoModeOnly = requiresVideoModeOnly,
                flashMode = flashMode,
                requireLocation = requireLocation,
                settings = settings,
                modeSettings = modeSettings,
                session = session,
            ),
        )
    }

    private fun sessionWithFlash(): ViewfinderSessionState {
        return ViewfinderSessionState(isFlashAvailable = true)
    }

    @Test
    fun flash_followsTheModeItWasSetTo() {
        val on = map(flashMode = FlashMode.ON, session = sessionWithFlash())
        val auto = map(flashMode = FlashMode.AUTO, session = sessionWithFlash())
        val off = map(flashMode = FlashMode.OFF, session = sessionWithFlash())

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
            flashMode = FlashMode.ON,
            session = ViewfinderSessionState(isFlashAvailable = false),
        )

        assertEquals(R.drawable.flash_off_circle, state.flashIcon)
        assertEquals(R.string.flash_off, state.flashDescription)
    }

    @Test
    fun videoRows_areOnlyShownInVideoMode() {
        val video = map(mode = CameraMode.VIDEO)
        val photo = map(mode = CameraMode.CAMERA)

        assertTrue(video.includeAudioSettingVisible)
        assertTrue(video.videoQualitySettingVisible)
        assertFalse(video.timerSettingVisible)

        assertFalse(photo.includeAudioSettingVisible)
        assertFalse(photo.videoQualitySettingVisible)
        assertTrue(photo.timerSettingVisible)
    }

    @Test
    fun stabilization_needsBothVideoModeAndACameraThatCanDoIt() {
        val capable = ViewfinderSessionState(canApplyVideoStabilization = true)

        assertTrue(map(mode = CameraMode.VIDEO, session = capable).stabilizationSettingVisible)
        assertFalse(map(mode = CameraMode.CAMERA, session = capable).stabilizationSettingVisible)
        assertFalse(map(mode = CameraMode.VIDEO).stabilizationSettingVisible)
    }

    @Test
    fun focusLockRow_isHiddenInAVideoOnlyEntryPoint() {
        assertTrue(map(mode = CameraMode.VIDEO).waitForFocusLockSettingVisible)
        assertFalse(map(requiresVideoModeOnly = true).waitForFocusLockSettingVisible)
    }

    @Test
    fun selfIlluminationRow_isForTheFrontLensOnly() {
        val front = ViewfinderSessionState(lensFacing = LensFacing.FRONT)
        val back = ViewfinderSessionState(lensFacing = LensFacing.BACK)

        assertTrue(map(session = front).selfIlluminationSettingVisible)
        assertFalse(map(session = back).selfIlluminationSettingVisible)
    }

    @Test
    fun toggles_readTheValuesTheyStandFor() {
        val state = map(
            requireLocation = true,
            settings = CameraSettings(includeAudio = true),
            modeSettings = ModeSettings(selfIllumination = true),
        )

        assertTrue(state.includeAudio)
        assertTrue(state.geoTagging)
        assertTrue(state.selfIllumination)
    }

    @Test
    fun selfIlluminationToggle_isTheStoredValueNotTheEffectiveOne() {
        val state = map(
            modeSettings = ModeSettings(selfIllumination = true),
            session = ViewfinderSessionState(lensFacing = LensFacing.BACK),
        )

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
    fun gridToggle_carriesADescriptionWithEveryIcon() {
        val byType = GridType.entries.associateWith { gridType ->
            map(settings = CameraSettings(gridType = gridType))
        }

        assertEquals(GridType.entries.size, byType.values.map { it.gridIcon }.toSet().size)
        assertEquals(GridType.entries.size, byType.values.map { it.gridDescription }.toSet().size)
        assertEquals(R.drawable.grid_off_circle, byType.getValue(GridType.NONE).gridIcon)
        assertEquals(R.string.grid_off, byType.getValue(GridType.NONE).gridDescription)
    }
}
