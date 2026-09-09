package app.grapheneos.camera.ui.viewfinder.screen.mapper

import androidx.camera.core.ImageCapture
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderUiStateMapperTest {

    private val mapper: ViewfinderUiStateMapper = ViewfinderUiStateMapperImpl(
        settingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl(),
    )

    private fun map(
        mode: CameraMode = CameraMode.CAMERA,
        isVideoMode: Boolean = false,
        settings: CameraSettings = CameraSettings(),
    ): ViewfinderUiState {
        return mapper.map(
            mode = mode,
            isVideoMode = isVideoMode,
            flashMode = ImageCapture.FLASH_MODE_OFF,
            settings = settings,
            session = ViewfinderSessionState(),
        )
    }

    @Test
    fun qrMode_showsTheOverlayAndTurnsTheShutterIntoATorch() {
        val state = map(mode = CameraMode.QR_SCAN)

        assertTrue(state.qrOverlayVisible)
        assertFalse(state.thirdOptionVisible)
        assertFalse(state.cancelButtonVisible)
        assertEquals(R.drawable.torch_off_button, state.captureButtonIcon)
        assertEquals(R.string.turn_torch_on, state.captureButtonDescription)
        assertEquals(android.R.color.transparent, state.captureButtonBackground)
    }

    @Test
    fun qrMode_scanningAllCodes_offersToStopInsteadOfTheFormatToggles() {
        val scanningAll = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(scanAllCodes = true),
        )
        val scanningSome = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(scanAllCodes = false),
        )

        assertFalse(scanningAll.qrScanTogglesVisible)
        assertEquals(R.drawable.cancel, scanningAll.flipCameraIcon)
        assertEquals(R.string.stop_scanning_all_formats, scanningAll.flipCameraDescription)

        assertTrue(scanningSome.qrScanTogglesVisible)
        assertEquals(R.drawable.auto, scanningSome.flipCameraIcon)
        assertEquals(R.string.scan_all_formats, scanningSome.flipCameraDescription)
    }

    @Test
    fun videoMode_announcesRecordingAndKeepsTheFlipCameraIcon() {
        val state = map(isVideoMode = true)

        assertEquals(R.drawable.recording, state.captureButtonIcon)
        assertEquals(R.string.start_recording, state.captureButtonDescription)
        assertEquals(R.drawable.flip_camera, state.flipCameraIcon)
        assertEquals(R.drawable.cbutton_bg, state.captureButtonBackground)
    }

    @Test
    fun micMutedIcon_isOnlyForAVideoModeRecordingWithoutAudio() {
        val silentVideo = map(
            isVideoMode = true,
            settings = CameraSettings(includeAudio = false),
        )
        val audibleVideo = map(
            isVideoMode = true,
            settings = CameraSettings(includeAudio = true),
        )
        val photo = map(
            isVideoMode = false,
            settings = CameraSettings(includeAudio = false),
        )
        val qr = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(includeAudio = false),
        )

        assertTrue(silentVideo.micMutedIconVisible)
        assertFalse(audibleVideo.micMutedIconVisible)
        assertFalse(photo.micMutedIconVisible)
        assertFalse(qr.micMutedIconVisible)
    }

    @Test
    fun selfTimerBadge_isHiddenWhereNoCountdownRuns() {
        val photo = map(settings = CameraSettings(selfTimerDurationSeconds = SOME_SECONDS))
        val video = map(
            isVideoMode = true,
            settings = CameraSettings(selfTimerDurationSeconds = SOME_SECONDS),
        )
        val qr = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(selfTimerDurationSeconds = SOME_SECONDS),
        )

        assertTrue(photo.selfTimerBadgeVisible)
        assertEquals("${SOME_SECONDS}s", photo.selfTimerBadge)

        assertFalse(video.selfTimerBadgeVisible)
        assertFalse(qr.selfTimerBadgeVisible)
    }

    @Test
    fun selfTimerBadge_unset_isEmptyAndHidden() {
        val state = map(settings = CameraSettings(selfTimerDurationSeconds = 0))

        assertEquals("", state.selfTimerBadge)
        assertFalse(state.selfTimerBadgeVisible)
    }

    private companion object {
        const val SOME_SECONDS = 3
    }
}
