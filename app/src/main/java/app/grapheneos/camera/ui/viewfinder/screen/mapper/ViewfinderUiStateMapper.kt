package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureButtonUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.util.formatVideoDuration
import javax.inject.Inject

interface ViewfinderUiStateMapper {
    fun map(state: ViewfinderState): ViewfinderUiState
}

internal class ViewfinderUiStateMapperImpl @Inject constructor(
    private val settingsSheetUiStateMapper: SettingsSheetUiStateMapper,
    private val captureUiStateMapper: CaptureUiStateMapper,
) : ViewfinderUiStateMapper {

    override fun map(state: ViewfinderState): ViewfinderUiState {
        val settings = state.settings
        val isVideoMode = state.isVideoMode()
        val inPhotoMode = state.isInPhotoMode()
        val isRecordingActive = state.recording.isActive()
        val isRecording = state.recording.isRecording()

        val chrome = when {
            state.isQrMode() -> qrState(
                settings = settings,
                isTorchOn = state.session.isTorchOn,
            )

            else -> withRecording(
                chrome = captureState(
                    state = state,
                    isVideoMode = isVideoMode,
                ),
                recording = state.recording,
            )
        }

        return chrome.copy(
            captureButton = chrome.captureButton.copy(
                visible = !state.capture.isCapturedPreviewShown,
                enabled = !state.capture.isTakingPicture,
                recording = isRecordingActive,
            ),
            isRecordingActive = isRecordingActive,
            isRecordingPaused = state.recording.isPaused,
            isRecordingMuted = state.recording.isMuted,
            muteToggleVisible = isRecording && settings.includeAudio,
            keepScreenOn = isRecordingActive,
            recordingTimerText = formatVideoDuration(state.recording.duration.inWholeSeconds),
            cameraPreviewVisible = !state.capture.isCapturedPreviewShown,
            modeTabsVisible = modeTabsVisible(state),
            thumbnailLoaderVisible = state.capture.isSavingPicture,
            capturedPreviewVisible = state.capture.isCapturedPreviewShown,
            isRecordingBeingSaved = state.capture.isSavingRecording,
            qrResultVisible = state.session.isQrResultShown,
            gridType = settings.gridType,
            mode = state.mode,
            aspectRatio = state.aspectRatio(),
            isQrMode = state.isQrMode(),
            isVideoMode = isVideoMode,
            inPhotoMode = inPhotoMode,
            scanAllCodes = settings.scanAllCodes,
            gyroscopeSuggestionsVisible = inPhotoMode && settings.gyroscopeSuggestions,
            availableModes = state.session.availableModes,
            zslSupported = state.session.isZslSupported,
            sensorOrientationDegrees = state.session.sensorOrientationDegrees,
            zoom = zoomState(state.session.zoom),
            exposure = exposureState(state.session.exposure),
            settingsSheet = settingsSheetUiStateMapper.map(state),
            capture = captureUiStateMapper.map(state),
        )
    }

    private fun zoomState(zoom: CameraZoom?): ZoomUiState {
        return when (zoom) {
            null -> ZoomUiState()
            else -> ZoomUiState(
                zoomRatio = zoom.zoomRatio,
                linearZoom = zoom.linearZoom,
            )
        }
    }

    private fun exposureState(exposure: CameraExposure?): ExposureUiState? {
        return when (exposure) {
            null -> null
            else -> ExposureUiState(
                min = exposure.compensationRange.first,
                max = exposure.compensationRange.last,
                progress = exposure.compensationIndex,
            )
        }
    }

    private fun qrState(
        settings: CameraSettings,
        isTorchOn: Boolean,
    ): ViewfinderUiState {
        return ViewfinderUiState(
            qrOverlayVisible = true,
            qrScanTogglesVisible = !settings.scanAllCodes,
            thirdOptionVisible = false,
            cancelButtonVisible = false,
            micMutedIconVisible = false,
            captureButton = CaptureButtonUiState(
                background = android.R.color.transparent,
                icon = when {
                    isTorchOn -> R.drawable.torch_on_button
                    else -> R.drawable.torch_off_button
                },
                description = when {
                    isTorchOn -> R.string.turn_torch_off
                    else -> R.string.turn_torch_on
                },
            ),
            flipCameraIcon = when {
                settings.scanAllCodes -> R.drawable.cancel
                else -> R.drawable.auto
            },
            flipCameraDescription = when {
                settings.scanAllCodes -> R.string.stop_scanning_all_formats
                else -> R.string.scan_all_formats
            },
        )
    }

    private fun captureState(
        state: ViewfinderState,
        isVideoMode: Boolean,
    ): ViewfinderUiState {
        val settings = state.settings
        val selfTimerSeconds = settings.selfTimerDurationSeconds
        val isSelfTimerRunning = state.capture.isSelfTimerRunning

        return ViewfinderUiState(
            qrOverlayVisible = false,
            qrScanTogglesVisible = false,
            thirdOptionVisible = thirdOptionVisible(state) && !isSelfTimerRunning,
            cancelButtonVisible = !isSelfTimerRunning,
            micMutedIconVisible = isVideoMode && !settings.includeAudio,
            captureButton = CaptureButtonUiState(
                icon = when {
                    isVideoMode -> R.drawable.recording
                    else -> R.drawable.camera_shutter
                },
                description = when {
                    isVideoMode -> R.string.start_recording
                    // The capture button cancels the countdown while one is up, so it must not
                    // keep announcing itself as the shutter. Only the description changes; the
                    // cross is drawn over the button.
                    isSelfTimerRunning -> R.string.cancel_timer
                    else -> R.string.capture
                },
            ),
            flipCameraIcon = R.drawable.flip_camera,
            flipCameraDescription = R.string.flip_camera,
            selfTimerBadge = when (selfTimerSeconds) {
                0 -> ""
                else -> "${selfTimerSeconds}s"
            },
            selfTimerBadgeVisible = selfTimerSeconds != 0 && !isVideoMode && !isSelfTimerRunning,
            selfTimerCountdownVisible = isSelfTimerRunning,
            selfTimerCancelVisible = isSelfTimerRunning,
        )
    }

    private fun withRecording(
        chrome: ViewfinderUiState,
        recording: ViewfinderRecordingState,
    ): ViewfinderUiState {
        if (!recording.isRecording()) return chrome

        return chrome.copy(
            cancelButtonVisible = false,
            recordingTimerVisible = true,
            // While recording, the gallery button turns into a shutter for stills
            thirdCircleIcon = R.drawable.camera_shutter,
            thirdCircleDescription = R.string.capture,
            captureButton = chrome.captureButton.copy(description = R.string.stop_recording),
            flipCameraIcon = when {
                recording.isPaused -> R.drawable.play
                else -> R.drawable.pause
            },
            flipCameraDescription = when {
                recording.isPaused -> R.string.resume_recording
                else -> R.string.pause_recording
            },
        )
    }

    private fun modeTabsVisible(state: ViewfinderState): Boolean {
        return state.showsCameraModeTabs &&
            !state.recording.isRecording() &&
            !state.capture.isSelfTimerRunning
    }

    /**
     * The third circle opens the gallery, which a capture session must not reach; a video capture
     * session shows it only to play back the recording under review.
     */
    private fun thirdOptionVisible(state: ViewfinderState): Boolean {
        return when {
            state.isCaptureSession -> {
                state.requiresVideoModeOnly && state.capture.isCapturedPreviewShown
            }

            state.requiresVideoModeOnly -> !state.recording.isRecording()

            else -> true
        }
    }
}
