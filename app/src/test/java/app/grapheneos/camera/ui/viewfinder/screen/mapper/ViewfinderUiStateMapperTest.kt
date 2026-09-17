package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderUiStateMapperTest {

    private val mapper: ViewfinderUiStateMapper = ViewfinderUiStateMapperImpl(
        settingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl(),
        captureUiStateMapper = CaptureUiStateMapperImpl(),
    )

    private fun map(
        mode: CameraMode = CameraMode.CAMERA,
        requiresVideoModeOnly: Boolean = false,
        isCaptureSession: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
        capture: ViewfinderCaptureState = ViewfinderCaptureState(),
    ): ViewfinderUiState {
        return mapper.map(
            ViewfinderState(
                mode = mode,
                requiresVideoModeOnly = requiresVideoModeOnly,
                isCaptureSession = isCaptureSession,
                settings = settings,
                session = session,
                capture = capture,
            ),
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
        val state = map(mode = CameraMode.VIDEO)

        assertEquals(R.drawable.recording, state.captureButtonIcon)
        assertEquals(R.string.start_recording, state.captureButtonDescription)
        assertEquals(R.drawable.flip_camera, state.flipCameraIcon)
        assertEquals(R.drawable.cbutton_bg, state.captureButtonBackground)
    }

    @Test
    fun videoOnlyEntryPoint_recordsWhicheverModeIsSelected() {
        val state = map(mode = CameraMode.CAMERA, requiresVideoModeOnly = true)

        assertTrue(state.isVideoMode)
        assertFalse(state.inPhotoMode)
        assertEquals(R.drawable.recording, state.captureButtonIcon)
        assertEquals(AspectRatio.RATIO_16_9, state.aspectRatio)
    }

    @Test
    fun micMutedIcon_isOnlyForAVideoModeRecordingWithoutAudio() {
        val silentVideo = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = false),
        )
        val audibleVideo = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = true),
        )
        val photo = map(
            mode = CameraMode.CAMERA,
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
            mode = CameraMode.VIDEO,
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

    @Test
    fun qrMode_withTheTorchOn_offersToTurnItOffWhateverTheFormatsScanned() {
        val state = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(scanAllCodes = true),
            session = ViewfinderSessionState(isTorchOn = true),
        )

        assertEquals(R.drawable.torch_on_button, state.captureButtonIcon)
        assertEquals(R.string.turn_torch_off, state.captureButtonDescription)
    }

    @Test
    fun recording_keepsItsControlsWhenASettingChanges() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(gridType = GridType.GOLDEN_RATIO),
            capture = ViewfinderCaptureState(isRecording = true),
        )

        assertFalse(state.cancelButtonVisible)
        assertTrue(state.thirdOptionVisible)
        assertEquals(R.drawable.recording, state.captureButtonIcon)
        assertEquals(R.string.stop_recording, state.captureButtonDescription)
        assertEquals(R.drawable.pause, state.flipCameraIcon)
        assertEquals(R.string.pause_recording, state.flipCameraDescription)
    }

    @Test
    fun pausedRecording_offersToResume() {
        val state = map(
            mode = CameraMode.VIDEO,
            capture = ViewfinderCaptureState(isRecording = true, isRecordingPaused = true),
        )

        assertEquals(R.drawable.play, state.flipCameraIcon)
        assertEquals(R.string.resume_recording, state.flipCameraDescription)
    }

    @Test
    fun aPauseWithoutARecording_leavesTheLensSwitch() {
        val state = map(
            mode = CameraMode.VIDEO,
            capture = ViewfinderCaptureState(isRecordingPaused = true),
        )

        assertEquals(R.drawable.flip_camera, state.flipCameraIcon)
        assertEquals(R.string.start_recording, state.captureButtonDescription)
        assertTrue(state.cancelButtonVisible)
    }

    @Test
    fun imageCaptureSession_neverOffersTheGallery() {
        val waiting = map(isCaptureSession = true)
        val reviewing = map(
            isCaptureSession = true,
            capture = ViewfinderCaptureState(isCapturedPreviewShown = true),
        )

        assertFalse(waiting.thirdOptionVisible)
        assertFalse(reviewing.thirdOptionVisible)
    }

    @Test
    fun videoCaptureSession_offersThePlayerOnlyForARecordingUnderReview() {
        val waiting = map(isCaptureSession = true, requiresVideoModeOnly = true)
        val recording = map(
            isCaptureSession = true,
            requiresVideoModeOnly = true,
            capture = ViewfinderCaptureState(isRecording = true),
        )
        val reviewing = map(
            isCaptureSession = true,
            requiresVideoModeOnly = true,
            capture = ViewfinderCaptureState(isCapturedPreviewShown = true),
        )

        assertFalse(waiting.thirdOptionVisible)
        assertFalse(recording.thirdOptionVisible)
        assertFalse(recording.cancelButtonVisible)
        assertTrue(reviewing.thirdOptionVisible)
        assertTrue(reviewing.capturedPreviewVisible)
    }

    @Test
    fun videoOnlySession_hidesTheThirdOptionWhileRecording() {
        val idle = map(requiresVideoModeOnly = true)
        val recording = map(
            requiresVideoModeOnly = true,
            capture = ViewfinderCaptureState(isRecording = true),
        )

        assertTrue(idle.thirdOptionVisible)
        assertFalse(recording.thirdOptionVisible)
    }

    private companion object {
        const val SOME_SECONDS = 3
    }

    @Test
    fun cameraState_isPublishedForTheViews() {
        val modes = setOf(CameraMode.CAMERA, CameraMode.VIDEO)

        val state = map(
            session = ViewfinderSessionState(
                isZslSupported = true,
                sensorOrientationDegrees = 270,
                availableModes = modes,
                zoom = CameraZoom(
                    zoomRatio = 2f,
                    linearZoom = 0.5f,
                    minZoomRatio = 1f,
                    maxZoomRatio = 10f,
                ),
                exposure = CameraExposure(
                    compensationIndex = 3,
                    compensationRange = -12..12,
                ),
            ),
        )

        assertTrue(state.zslSupported)
        assertEquals(270, state.sensorOrientationDegrees)
        assertEquals(modes, state.availableModes)
        assertEquals(ZoomUiState(zoomRatio = 2f, linearZoom = 0.5f), state.zoom)
        assertEquals(ExposureUiState(min = -12, max = 12, progress = 3), state.exposure)
    }

    @Test
    fun cameraState_beforeTheCameraIsBound_showsNoZoomAndNoExposure() {
        val state = map()

        assertEquals(ZoomUiState(), state.zoom)
        assertNull(state.exposure)
        assertNull(state.sensorOrientationDegrees)
    }
}
