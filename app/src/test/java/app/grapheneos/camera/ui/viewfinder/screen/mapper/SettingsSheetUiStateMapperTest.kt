package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.SettingsSheetUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
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
        isVideoMode: Boolean = false,
        flashMode: Int = ImageCapture.FLASH_MODE_OFF,
        requireLocation: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
    ): SettingsSheetUiState {
        return mapper.map(
            isVideoMode = isVideoMode,
            flashMode = flashMode,
            requireLocation = requireLocation,
            settings = settings,
            modeSettings = modeSettings,
            session = session,
        )
    }

    private fun sessionWithFlash(): ViewfinderSessionState {
        return ViewfinderSessionState(isFlashAvailable = true)
    }

    @Test
    fun flash_followsTheModeItWasSetTo() {
        val on = map(flashMode = ImageCapture.FLASH_MODE_ON, session = sessionWithFlash())
        val auto = map(flashMode = ImageCapture.FLASH_MODE_AUTO, session = sessionWithFlash())
        val off = map(flashMode = ImageCapture.FLASH_MODE_OFF, session = sessionWithFlash())

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
            flashMode = ImageCapture.FLASH_MODE_ON,
            session = ViewfinderSessionState(isFlashAvailable = false),
        )

        assertEquals(R.drawable.flash_off_circle, state.flashIcon)
        assertEquals(R.string.flash_off, state.flashDescription)
    }

    @Test
    fun videoRows_areOnlyShownInVideoMode() {
        val video = map(isVideoMode = true)
        val photo = map(isVideoMode = false)

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

        assertTrue(map(isVideoMode = true, session = capable).stabilizationSettingVisible)
        assertFalse(map(isVideoMode = false, session = capable).stabilizationSettingVisible)
        assertFalse(map(isVideoMode = true).stabilizationSettingVisible)
    }

    @Test
    fun selfIlluminationRow_isForTheFrontLensOnly() {
        val front = ViewfinderSessionState(lensFacing = CameraSelector.LENS_FACING_FRONT)
        val back = ViewfinderSessionState(lensFacing = CameraSelector.LENS_FACING_BACK)

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
            session = ViewfinderSessionState(lensFacing = CameraSelector.LENS_FACING_BACK),
        )

        assertTrue(state.selfIllumination)
        assertFalse(state.selfIlluminationSettingVisible)
    }
}
