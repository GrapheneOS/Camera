package app.grapheneos.camera.ui.viewfinder.screen.mapper

import android.net.Uri
import android.view.Surface
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureResult
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderFocusRequest
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderPreviewControlsState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureResultUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ExposureUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FocusRingUiModel
import kotlin.time.Duration
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

    private val mapper: ViewfinderUiStateMapper = ViewfinderUiStateMapperImpl()

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
    fun aCaptureResult_takesTheShutterOffTheScreen() {
        val image = createBitmap(1, 1)
        val shown = map(
            capture = ViewfinderCaptureState(result = ViewfinderCaptureResult.Photo(image)),
        )

        assertFalse(shown.captureButton.visible)
        assertEquals(CaptureResultUiModel(visible = true, image = image), shown.captureResult)

        assertEquals(CaptureResultUiModel(), map().captureResult)
        assertTrue(map().captureButton.visible)
    }

    @Test
    fun aHeldShutter_staysOffTheScreen() {
        val held = map(capture = ViewfinderCaptureState(isShutterHeld = true))

        assertFalse(held.captureButton.visible)
    }

    @Test
    fun captureSessions_doNotBlurTheLastFrame() {
        assertTrue(map().preview.blursLastFrame)
        assertFalse(map(isCaptureSession = true).preview.blursLastFrame)
    }

    @Test
    fun recordingTimer_showsTheRecordedDuration() {
        assertEquals("00:00", map().recording.timerText)
        assertEquals(
            "01:05",
            map(recording = ViewfinderRecordingState(duration = 65.seconds))
                .recording.timerText,
        )
    }

    @Test
    fun modeTabs_stayHiddenWhileRecordingOrCountingDownAndWhereTheyAreNotBuilt() {
        assertTrue(map().modeTabs.visible)
        assertFalse(map(showsCameraModeTabs = false).modeTabs.visible)
        assertFalse(
            map(recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING))
                .modeTabs.visible,
        )
        assertFalse(
            map(capture = ViewfinderCaptureState(selfTimerRemaining = 3.seconds)).modeTabs.visible,
        )
    }

    @Test
    fun qrMode_showsTheOverlayAndTurnsTheShutterIntoATorch() {
        val state = map(mode = CameraMode.QR_SCAN)

        assertTrue(state.qr.overlayVisible)
        assertFalse(state.thirdOption.visible)
        assertFalse(state.cancelButtonVisible)
        assertEquals(R.drawable.torch_off_button, state.captureButton.icon)
        assertEquals(R.string.turn_torch_on, state.captureButton.description)
        assertEquals(android.R.color.transparent, state.captureButton.background)
    }

    @Test
    fun volumeKeys_leftToChangeTheVolume_areNotHandled() {
        val handled = VolumeKeyAction.entries.associateWith { action ->
            map(settings = CameraSettings(volumeKeyAction = action)).handlesVolumeKeys
        }

        assertEquals(
            mapOf(
                VolumeKeyAction.SHUTTER to true,
                VolumeKeyAction.ZOOM to true,
                VolumeKeyAction.VOLUME to false,
                VolumeKeyAction.OFF to true,
            ),
            handled,
        )
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

        assertFalse(scanningAll.qr.scanTogglesVisible)
        assertEquals(R.drawable.cancel, scanningAll.flipCamera.icon)
        assertEquals(R.string.stop_scanning_all_formats, scanningAll.flipCamera.description)

        assertTrue(scanningSome.qr.scanTogglesVisible)
        assertEquals(R.drawable.auto, scanningSome.flipCamera.icon)
        assertEquals(R.string.scan_all_formats, scanningSome.flipCamera.description)
    }

    @Test
    fun videoMode_announcesRecordingAndKeepsTheFlipCameraIcon() {
        val state = map(mode = CameraMode.VIDEO)

        assertEquals(R.drawable.recording, state.captureButton.icon)
        assertEquals(R.string.start_recording, state.captureButton.description)
        assertEquals(R.drawable.flip_camera, state.flipCamera.icon)
        assertEquals(R.drawable.cbutton_bg, state.captureButton.background)
    }

    @Test
    fun icons_turnAgainstTheDeviceRelativeToTheDisplay() {
        val state = mapper.map(
            ViewfinderState(
                mode = CameraMode.CAMERA,
                requiresVideoModeOnly = false,
                deviceOrientation = DeviceOrientation.DEGREES_90,
                displayRotation = Surface.ROTATION_90,
                autoRotateEnabled = true,
            ),
        )

        assertEquals(180f, state.rotation.iconDegrees)
        assertEquals(180f, state.rotation.levelFrameDegrees)
    }

    @Test
    fun icons_withoutAutoRotate_stayUprightWhileTheLevelFrameStillTurns() {
        val state = mapper.map(
            ViewfinderState(
                mode = CameraMode.CAMERA,
                requiresVideoModeOnly = false,
                deviceOrientation = DeviceOrientation.DEGREES_90,
                autoRotateEnabled = false,
            ),
        )

        assertEquals(0f, state.rotation.iconDegrees)
        assertEquals(270f, state.rotation.levelFrameDegrees)
    }

    @Test
    fun icons_duringARecording_keepTheOrientationItStartedIn() {
        val state = mapper.map(
            ViewfinderState(
                mode = CameraMode.VIDEO,
                requiresVideoModeOnly = false,
                deviceOrientation = DeviceOrientation.DEGREES_90,
                autoRotateEnabled = true,
                recording = ViewfinderRecordingState(
                    phase = RecordingPhase.RECORDING,
                    orientationAtStart = DeviceOrientation.DEGREES_0,
                ),
            ),
        )

        assertEquals(0f, state.rotation.iconDegrees)
    }

    @Test
    fun icons_beforeTheSensorHasSpoken_stayUpright() {
        assertEquals(0f, map().rotation.iconDegrees)
        assertEquals(0f, map().rotation.levelFrameDegrees)
    }

    @Test
    fun videoOnlyEntryPoint_showsVideoControls() {
        val state = map(mode = CameraMode.VIDEO, requiresVideoModeOnly = true)

        assertEquals(CameraMode.VIDEO, state.mode)
        assertEquals(R.drawable.recording, state.captureButton.icon)
        assertEquals(AspectRatio.RATIO_16_9, state.preview.aspectRatio)
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

        assertTrue(silentVideo.recording.micOffIconVisible)
        assertFalse(audibleVideo.recording.micOffIconVisible)
        assertFalse(photo.recording.micOffIconVisible)
        assertFalse(qr.recording.micOffIconVisible)
    }

    @Test
    fun selfTimerBadge_isHiddenWhereNoCountdownRuns() {
        val photo = map(settings = CameraSettings(selfTimerDuration = SOME_SECONDS.seconds))
        val video = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(selfTimerDuration = SOME_SECONDS.seconds),
        )
        val qr = map(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(selfTimerDuration = SOME_SECONDS.seconds),
        )

        assertTrue(photo.selfTimer.badgeVisible)
        assertEquals("${SOME_SECONDS}s", photo.selfTimer.badge)

        assertFalse(video.selfTimer.badgeVisible)
        assertFalse(qr.selfTimer.badgeVisible)
    }

    @Test
    fun selfTimerBadge_unset_isEmptyAndHidden() {
        val state = map(settings = CameraSettings(selfTimerDuration = Duration.ZERO))

        assertEquals("", state.selfTimer.badge)
        assertFalse(state.selfTimer.badgeVisible)
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
    fun captureButton_isDisabledOnlyWhileAPictureIsTaken() {
        val taking = map(capture = ViewfinderCaptureState(isTakingPicture = true))

        assertFalse(taking.captureButton.enabled)
        assertTrue(map().captureButton.enabled)
    }

    @Test
    fun selfTimerCountdown_hidesTheControlsItWouldRaceAndOffersToCancel() {
        val state = map(
            settings = CameraSettings(selfTimerDuration = 5.seconds),
            capture = ViewfinderCaptureState(selfTimerRemaining = 3.seconds),
        )

        assertFalse(state.thirdOption.visible)
        assertFalse(state.cancelButtonVisible)
        assertFalse(state.flipCamera.visible)
        assertFalse(state.selfTimer.badgeVisible)
        assertEquals(3.seconds, state.selfTimer.remaining)
        assertEquals(R.string.cancel_timer, state.captureButton.description)
    }

    @Test
    fun noSelfTimerCountdown_leavesTheShutterAndBadge() {
        val state = map(settings = CameraSettings(selfTimerDuration = 5.seconds))

        assertTrue(state.thirdOption.visible)
        assertTrue(state.cancelButtonVisible)
        assertTrue(state.flipCamera.visible)
        assertTrue(state.selfTimer.badgeVisible)
        assertNull(state.selfTimer.remaining)
        assertEquals(R.string.capture, state.captureButton.description)
    }

    @Test
    fun thumbnailLoader_isShownOnlyWhileAPictureIsSaved() {
        val saving = map(capture = ViewfinderCaptureState(isSavingPicture = true))

        assertTrue(saving.thirdOption.thumbnailLoaderVisible)
        assertFalse(map().thirdOption.thumbnailLoaderVisible)
    }

    @Test
    fun recording_keepsItsControlsWhenASettingChanges() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(gridType = GridType.GOLDEN_RATIO),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertFalse(state.cancelButtonVisible)
        assertTrue(state.thirdOption.visible)
        assertEquals(R.drawable.recording, state.captureButton.icon)
        assertEquals(R.string.stop_recording, state.captureButton.description)
        assertEquals(R.drawable.pause, state.flipCamera.icon)
        assertEquals(R.string.pause_recording, state.flipCamera.description)
    }

    @Test
    fun startingRecording_isActiveButKeepsTheIdleControls() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertTrue(state.recording.isActive)
        assertTrue(state.cancelButtonVisible)
        assertEquals(R.string.start_recording, state.captureButton.description)
    }

    @Test
    fun isRecordingActive_holdsOnlyWhileARecordingIsUnderway() {
        val recording = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertTrue(recording.recording.isActive)
        assertFalse(map(mode = CameraMode.VIDEO).recording.isActive)
    }

    @Test
    fun recordingWithAudio_offersTheMuteToggle() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = true),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING, isMuted = true),
        )

        assertTrue(state.recording.muteToggleVisible)
        assertTrue(state.recording.isMuted)
    }

    @Test
    fun recordingWithoutAudio_hidesTheMuteToggle() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = false),
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertFalse(state.recording.muteToggleVisible)
    }

    @Test
    fun startingRecording_hidesTheMuteToggleUntilItStarts() {
        val state = map(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(includeAudio = true),
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertFalse(state.recording.muteToggleVisible)
    }

    @Test
    fun recording_turnsTheGalleryButtonIntoAShutterAndShowsTheTimer() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertEquals(R.drawable.camera_shutter, state.thirdOption.icon)
        assertEquals(R.string.capture, state.thirdOption.description)
        assertTrue(state.recording.timerVisible)
        assertTrue(state.keepScreenOn)
    }

    @Test
    fun startingRecording_keepsTheScreenOnBeforeTheChromeChanges() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING),
        )

        assertTrue(state.keepScreenOn)
        assertEquals(R.drawable.option_circle, state.thirdOption.icon)
        assertFalse(state.recording.timerVisible)
    }

    @Test
    fun noRecording_opensTheGalleryAndLetsTheScreenSleep() {
        val state = map(mode = CameraMode.VIDEO)

        assertEquals(R.drawable.option_circle, state.thirdOption.icon)
        assertEquals(R.string.open_gallery, state.thirdOption.description)
        assertFalse(state.recording.timerVisible)
        assertFalse(state.keepScreenOn)
    }

    @Test
    fun pausedRecording_offersToResume() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING, isPaused = true),
        )

        assertEquals(R.drawable.play, state.flipCamera.icon)
        assertEquals(R.string.resume_recording, state.flipCamera.description)
    }

    @Test
    fun aPauseWithoutARecording_leavesTheLensSwitch() {
        val state = map(
            mode = CameraMode.VIDEO,
            recording = ViewfinderRecordingState(isPaused = true),
        )

        assertEquals(R.drawable.flip_camera, state.flipCamera.icon)
        assertEquals(R.string.start_recording, state.captureButton.description)
        assertTrue(state.cancelButtonVisible)
    }

    @Test
    fun imageCaptureSession_neverOffersTheGallery() {
        val waiting = map(isCaptureSession = true)
        val reviewing = map(
            isCaptureSession = true,
            capture = ViewfinderCaptureState(
                result = ViewfinderCaptureResult.Photo(image = createBitmap(1, 1)),
            ),
        )

        assertFalse(waiting.thirdOption.visible)
        assertFalse(reviewing.thirdOption.visible)
    }

    @Test
    fun videoCaptureSession_offersThePlayerOnlyForARecordingUnderReview() {
        val waiting = map(
            mode = CameraMode.VIDEO,
            isCaptureSession = true,
            requiresVideoModeOnly = true,
        )
        val recording = map(
            mode = CameraMode.VIDEO,
            isCaptureSession = true,
            requiresVideoModeOnly = true,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )
        val reviewing = map(
            mode = CameraMode.VIDEO,
            isCaptureSession = true,
            requiresVideoModeOnly = true,
            capture = ViewfinderCaptureState(
                result = ViewfinderCaptureResult.Video(uri = Uri.EMPTY, image = null),
            ),
        )

        assertFalse(waiting.thirdOption.visible)
        assertFalse(recording.thirdOption.visible)
        assertFalse(recording.cancelButtonVisible)
        assertTrue(reviewing.thirdOption.visible)
        assertTrue(reviewing.captureResult.visible)
    }

    @Test
    fun videoOnlySession_hidesTheThirdOptionWhileRecording() {
        val idle = map(mode = CameraMode.VIDEO, requiresVideoModeOnly = true)
        val recording = map(
            mode = CameraMode.VIDEO,
            requiresVideoModeOnly = true,
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertTrue(idle.thirdOption.visible)
        assertFalse(recording.thirdOption.visible)
    }

    @Test
    fun cameraState_isPublishedForTheViews() {
        val modes = setOf(CameraMode.CAMERA, CameraMode.VIDEO)

        val state = map(
            session = ViewfinderSessionState(
                sensorOrientationDegrees = 270,
                availableModes = modes,
                exposure = CameraExposure(
                    compensationIndex = 3,
                    compensationRange = -12..12,
                ),
            ),
        )

        assertEquals(270, state.preview.sensorOrientationDegrees)
        assertEquals(modes, state.modeTabs.availableModes)
        assertEquals(ExposureUiModel(range = -12..12, progress = 3), state.exposure)
    }

    @Test
    fun cameraState_beforeTheCameraIsBound_showsNoExposure() {
        val state = map()

        assertNull(state.exposure.range)
        assertNull(state.preview.sensorOrientationDegrees)
    }

    @Test
    fun exposurePanel_followsItsState() {
        val state = mapper.map(
            ViewfinderState(
                mode = CameraMode.CAMERA,
                requiresVideoModeOnly = false,
                previewControls = ViewfinderPreviewControlsState(isExposurePanelShown = true),
            ),
        )

        assertTrue(state.exposure.panelVisible)
    }

    @Test
    fun focusRing_isTheLastFocusRequest() {
        val focused = mapper.map(
            ViewfinderState(
                mode = CameraMode.CAMERA,
                requiresVideoModeOnly = false,
                previewControls = ViewfinderPreviewControlsState(
                    focusRequest = ViewfinderFocusRequest(x = 10f, y = 20f, id = 3),
                ),
            ),
        )

        assertEquals(FocusRingUiModel(x = 10f, y = 20f, id = 3), focused.focusRing)
        assertNull(map().focusRing)
    }

    @Test
    fun flipCamera_showsTheBoundLensInEveryMode() {
        val front = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
            session = ViewfinderSessionState(lensFacing = LensFacing.FRONT),
        )

        assertEquals(LensFacing.FRONT, mapper.map(front).flipCamera.lensFacing)
        assertEquals(
            LensFacing.FRONT,
            mapper.map(front.copy(mode = CameraMode.QR_SCAN)).flipCamera.lensFacing,
        )
        assertEquals(LensFacing.BACK, map().flipCamera.lensFacing)
    }

    @Test
    fun screenWake_keepsTheScreenOnWithoutARecording() {
        assertTrue(map(keepsScreenAwake = true).keepScreenOn)
    }

    @Test
    fun zoom_isPublishedForTheZoomBar() {
        val state = stateWithZoom(
            zoom = CameraZoom(
                zoomRatio = 2f,
                linearZoom = 0.5f,
                minZoomRatio = 1f,
                maxZoomRatio = 10f,
            ),
        )

        assertEquals(ZoomUiState(zoomRatio = 2f, linearZoom = 0.5f), mapper.mapZoom(state))
    }

    @Test
    fun zoom_beforeTheCameraIsBound_isTheDefault() {
        assertEquals(ZoomUiState(), mapper.mapZoom(stateWithZoom(zoom = null)))
    }

    @Test
    fun zoomPanel_followsItsStateWithOrWithoutAZoom() {
        assertEquals(
            ZoomUiState(panelVisible = true),
            mapper.mapZoom(
                stateWithZoom(zoom = null).copy(
                    previewControls = ViewfinderPreviewControlsState(isZoomPanelShown = true),
                ),
            ),
        )
    }

    private fun stateWithZoom(zoom: CameraZoom?): ViewfinderState {
        return ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
            session = ViewfinderSessionState(zoom = zoom),
        )
    }

    private fun map(
        mode: CameraMode = CameraMode.CAMERA,
        requiresVideoModeOnly: Boolean = false,
        isCaptureSession: Boolean = false,
        showsCameraModeTabs: Boolean = true,
        settings: CameraSettings = CameraSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
        capture: ViewfinderCaptureState = ViewfinderCaptureState(),
        recording: ViewfinderRecordingState = ViewfinderRecordingState(),
        keepsScreenAwake: Boolean = false,
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
                keepsScreenAwake = keepsScreenAwake,
            ),
        )
    }

    private companion object {
        const val SOME_SECONDS = 3
    }
}
