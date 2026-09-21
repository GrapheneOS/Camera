package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureUiStateMapperTest {

    private val mapper: CaptureUiStateMapper = CaptureUiStateMapperImpl()

    private fun map(
        requireLocation: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
    ): CaptureUiState {
        return mapper.map(
            ViewfinderState(
                mode = CameraMode.CAMERA,
                requiresVideoModeOnly = false,
                requireLocation = requireLocation,
                settings = settings,
                modeSettings = modeSettings,
                session = session,
            ),
        )
    }

    @Test
    fun geoTagging_followsTheSettledValueNotTheStoredOne() {
        val storedOn = ModeSettings(geoTagging = true)

        assertFalse(map(requireLocation = false, modeSettings = storedOn).geoTagging)
        assertTrue(map(requireLocation = true).geoTagging)
    }

    @Test
    fun storedCaptureSettings_arePassedThrough() {
        val state = map(
            settings = CameraSettings(
                includeAudio = false,
                enableCameraSounds = false,
            ),
        )

        assertEquals(
            CaptureUiState(
                includeAudio = false,
                cameraSounds = false,
            ),
            state,
        )
    }
}
