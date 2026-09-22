package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import kotlin.time.Duration.Companion.seconds
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
        showsCameraModeTabs: Boolean = true,
        settings: CameraSettings = CameraSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
        capture: ViewfinderCaptureState = ViewfinderCaptureState(),
        recording: ViewfinderRecordingState = ViewfinderRecordingState(),
    ): ViewfinderUiState {
        return mapper.map(
            ViewfinderState(
                mode = mode,
                requiresVideoModeOnly = requiresVideoModeOnly,
                isCaptureSession = isCaptureSession,
                showsCameraModeTabs = showsCameraModeTabs,
                settings = settings,
                session = session,
                capture = capture,
                recording = recording,
            ),
        )
    }

    @Test
    fun captureButton_marksTheRecordingItStopsWhileItIsUnderway() {
        assertFalse(map().captureButton.recording)
        assertTrue(
            map(recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING))
                .captureButton.recording,
        )
        assertEquals(
            R.string.stop_recording,
            map(
                mode = CameraMode.VIDEO,
                recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
            ).captureButton.description,
        )
    }

    @Test
    fun recordingTimer_showsTheRecordedDuration() {
        assertEquals("00:00", map().recordingTimerText)
        assertEquals(
            "01:05",
            map(recording = ViewfinderRecordingState(duration = 65.seconds))
                .recordingTimerText,
        )
    }

    @Test
    fun modeTabs_stayHiddenWhileRecordingOrCountingDownAndWhereTheyAreNotBuilt() {
        assertTrue(map().modeTabsVisible)
        assertFalse(map(showsCameraModeTabs = false).modeTabsVisible)
        assertFalse(
            map(recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING))
                .modeTabsVisible,
        )
        assertFalse(
            map(capture = ViewfinderCaptureState(isSelfTimerRunning = true)).modeTabsVisible,
        )
    }

    @Test
    fun qrMode_showsTheOverlayAndTurnsTheShutterIntoATorch() {
        val state = map(mode = CameraMode.QR_SCAN)

        assertTrue(state.qrOverlayVisible)
        assertFalse(state.thirdOptionVisible)
        assertFalse(state.cancelButtonVisible)
        assertEquals(R.drawable.torch_off_button, state.captureButton.icon)
        assertEquals(R.string.turn_torch_on, state.captureButton.description)
        assertEquals(android.R.color.transparent, state.captureButton.background)
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

        assertEquals(R.drawable.recording, state.captureButton.icon)
        assertEquals(R.string.start_recording, state.captureButton.description)
        assertEquals(R.drawable.flip_camera, state.flipCameraIcon)
        assertEquals(R.drawable.cbutton_bg, state.captureButton.background)
    }

    @Test
    fun videoOnlyEntryPoint_recordsWhicheverModeIsSelected() {
        val state = map(mode = CameraMode.CAMERA, requiresVideoModeOnly = true)

        assertTrue(state.isVideoMode)
        assertFalse(state.inPhotoMode)
        assertEquals(R.drawable.recording, state.captureButton.icon)
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

        assertEquals(R.drawable.torch_on_button, state.captureButton.icon)
        assertEquals(R.string.turn_torch_off, state.captureButton.description)
    }

    @Test
    fun takingAPicture_disablesTheShutter() {
        val state = map(capture = ViewfinderCaptureState(isTakingPicture = true))

        assertFalse(state.captureButton.enabled)
    }

    @Test
    fun noPictureInProgress_leavesTheShutterEnabled() {
        assertTrue(map().captureButton.enabled)
    }

    @Test
    fun selfTimerCountdown_hidesTheControlsItWouldRaceAndOffersToCancel() {
        val state = map(
            settings = CameraSettings(selfTimerDurationSeconds = 5),
            capture = ViewfinderCaptureState(isSelfTimerRunning = true),
        )

        assertFalse(state.thirdOptionVisible)
        assertFalse(state.cancelButtonVisible)
        assertFalse(state.selfTimerBadgeVisible)
        assertTrue(state.selfTimerCountdownVisible)
        assertTrue(state.selfTimerCancelVisible)
        assertEquals(R.string.cancel_timer, state.captureButton.description)
    }

    @Test
    fun noSelfTimerCountdown_leavesTheShutterAndBadge() {
        val state = map(settings = CameraSettings(selfTimerDurationSeconds = 5))

        assertTrue(state.thirdOptionVisible)
        assertTrue(state.cancelButtonVisible)
        assertTrue(state.selfTimerBadgeVisible)
        assertFalse(state.selfTimerCountdownVisible)
        assertFalse(state.selfTimerCancelVisible)
        assertEquals(R.string.capture, state.captureButton.description)
    }

    @Test
    fun savingAPicture_showsTheThumbnailLoader() {
        val state = map(capture = ViewfinderCaptureState(isSavingPicture = true))

        assertTrue(state.thumbnailLoaderVisible)
    }

    @Test
    fun noPictureBeingSaved_hidesTheThumbnailLoader() {
        assertFalse(map().thumbnailLoaderVisible)
    }

    @Test
    fun recording_keepsItsControlsWhenASettingChanges() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(gridType = GridType.GOLDEN_RATIO),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertFalse(state.cancelButtonVisible)
        assertTrue(state.thirdOptionVisible)
        assertEquals(R.drawable.recording, state.captureButton.icon)
        assertEquals(R.string.stop_recording, state.captureButton.description)
        assertEquals(R.drawable.pause, state.flipCameraIcon)
        assertEquals(R.string.pause_recording, state.flipCameraDescription)
    }

    @Test
    fun startingRecording_isActiveButKeepsTheIdleControls() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertTrue(state.isRecordingActive)
        assertTrue(state.cancelButtonVisible)
        assertEquals(R.string.start_recording, state.captureButton.description)
    }

    @Test
    fun recording_isActive() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertTrue(state.isRecordingActive)
    }

    @Test
    fun recordingWithAudio_offersTheMuteToggle() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = true),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING, isMuted = true),
        )

        assertTrue(state.muteToggleVisible)
        assertTrue(state.isRecordingMuted)
    }

    @Test
    fun recordingWithoutAudio_hidesTheMuteToggle() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = false),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertFalse(state.muteToggleVisible)
    }

    @Test
    fun startingRecording_hidesTheMuteToggleUntilItStarts() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = true),
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertFalse(state.muteToggleVisible)
    }

    @Test
    fun recording_turnsTheGalleryButtonIntoAShutterAndShowsTheTimer() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertEquals(R.drawable.camera_shutter, state.thirdCircleIcon)
        assertEquals(R.string.capture, state.thirdCircleDescription)
        assertTrue(state.recordingTimerVisible)
        assertTrue(state.keepScreenOn)
    }

    @Test
    fun startingRecording_keepsTheScreenOnBeforeTheChromeChanges() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertTrue(state.keepScreenOn)
        assertEquals(R.drawable.option_circle, state.thirdCircleIcon)
        assertFalse(state.recordingTimerVisible)
    }

    @Test
    fun noRecording_opensTheGalleryAndLetsTheScreenSleep() {
        val state = map(mode = CameraMode.VIDEO)

        assertEquals(R.drawable.option_circle, state.thirdCircleIcon)
        assertEquals(R.string.open_gallery, state.thirdCircleDescription)
        assertFalse(state.recordingTimerVisible)
        assertFalse(state.keepScreenOn)
    }

    @Test
    fun noRecording_isNotActive() {
        assertFalse(map(mode = CameraMode.VIDEO).isRecordingActive)
    }

    @Test
    fun pausedRecording_offersToResume() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING, isPaused = true),
        )

        assertEquals(R.drawable.play, state.flipCameraIcon)
        assertEquals(R.string.resume_recording, state.flipCameraDescription)
    }

    @Test
    fun aPauseWithoutARecording_leavesTheLensSwitch() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(isPaused = true),
        )

        assertEquals(R.drawable.flip_camera, state.flipCameraIcon)
        assertEquals(R.string.start_recording, state.captureButton.description)
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
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
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
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
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
