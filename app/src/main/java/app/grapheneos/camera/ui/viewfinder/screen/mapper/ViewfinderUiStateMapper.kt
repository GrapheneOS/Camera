package app.grapheneos.camera.ui.viewfinder.screen.mapper

import android.view.Surface
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureButtonUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.FlipCameraUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ModeTabsUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.PreviewUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.QrUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.RotationUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.SelfTimerUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ThirdOptionUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.util.formatVideoDuration
import javax.inject.Inject
import kotlin.time.Duration

interface ViewfinderUiStateMapper {
    fun map(state: ViewfinderState): ViewfinderUiState
}

internal class ViewfinderUiStateMapperImpl @Inject constructor() : ViewfinderUiStateMapper {

    override fun map(state: ViewfinderState): ViewfinderUiState {
        val isRecordingActive = state.recording.isActive()

        return ViewfinderUiState(
            mode = state.mode,
            isVideoMode = state.isVideoMode(),
            preview = PreviewUiState(
                aspectRatio = state.aspectRatio(),
                sensorOrientationDegrees = state.session.sensorOrientationDegrees,
                gridType = state.settings.gridType,
            ),
            modeTabs = ModeTabsUiState(
                visible = modeTabsVisible(state),
                availableModes = state.session.availableModes,
            ),
            captureButton = captureButton(state),
            flipCamera = flipCamera(state),
            thirdOption = thirdOption(state),
            cancelButtonVisible = !state.isQrMode() &&
                !state.capture.isSelfTimerRunning() &&
                !state.recording.isRecording(),
            selfTimer = selfTimer(state),
            recording = recording(state),
            qr = QrUiState(
                overlayVisible = state.isQrMode(),
                scanTogglesVisible = state.isQrMode() && !state.settings.scanAllCodes,
            ),
            exposure = exposure(state),
            rotation = rotation(state),
            capturedPreviewVisible = state.capture.isCapturedPreviewShown,
            keepScreenOn = isRecordingActive || state.keepsScreenAwake,
            isSelfIlluminating = state.selfIlluminate(),
            isSettingsPanelAvailable = state.isSettingsPanelAvailable(),
            handlesVolumeKeys = state.settings.volumeKeyAction != VolumeKeyAction.VOLUME,
            cameraSoundsEnabled = state.settings.enableCameraSounds,
            dialog = state.dialog,
        )
    }

    private fun captureButton(state: ViewfinderState): CaptureButtonUiState {
        val isTorchOn = state.session.isTorchOn
        val common = CaptureButtonUiState(
            visible = !state.capture.isCapturedPreviewShown,
            enabled = !state.capture.isTakingPicture,
            recording = state.recording.isActive(),
        )

        return when {
            state.isQrMode() -> common.copy(
                background = android.R.color.transparent,
                icon = when {
                    isTorchOn -> R.drawable.torch_on_button
                    else -> R.drawable.torch_off_button
                },
                description = when {
                    isTorchOn -> R.string.turn_torch_off
                    else -> R.string.turn_torch_on
                },
            )

            else -> common.copy(
                icon = when {
                    state.isVideoMode() -> R.drawable.recording
                    else -> R.drawable.camera_shutter
                },
                description = when {
                    state.recording.isRecording() -> R.string.stop_recording
                    state.isVideoMode() -> R.string.start_recording
                    // The capture button cancels the countdown while one is up, so it must not
                    // keep announcing itself as the shutter. Only the description changes; the
                    // cross is drawn over the button.
                    state.capture.isSelfTimerRunning() -> R.string.cancel_timer
                    else -> R.string.capture
                },
            )
        }
    }

    private fun flipCamera(state: ViewfinderState): FlipCameraUiState {
        val visible = !state.capture.isSelfTimerRunning()

        return when {
            state.isQrMode() -> scanAllCodesToggle(state.settings.scanAllCodes)

            state.recording.isRecording() -> recordingPauseToggle(
                paused = state.recording.isPaused,
                visible = visible,
            )

            else -> FlipCameraUiState(visible = visible)
        }
    }

    private fun scanAllCodesToggle(scanAllCodes: Boolean): FlipCameraUiState {
        return FlipCameraUiState(
            icon = when {
                scanAllCodes -> R.drawable.cancel
                else -> R.drawable.auto
            },
            description = when {
                scanAllCodes -> R.string.stop_scanning_all_formats
                else -> R.string.scan_all_formats
            },
        )
    }

    private fun recordingPauseToggle(
        paused: Boolean,
        visible: Boolean,
    ): FlipCameraUiState {
        return FlipCameraUiState(
            visible = visible,
            icon = when {
                paused -> R.drawable.play
                else -> R.drawable.pause
            },
            description = when {
                paused -> R.string.resume_recording
                else -> R.string.pause_recording
            },
        )
    }

    private fun thirdOption(state: ViewfinderState): ThirdOptionUiState {
        // While recording, the gallery button turns into a shutter for stills
        val takesStills = !state.isQrMode() && state.recording.isRecording()

        return ThirdOptionUiState(
            visible = !state.isQrMode() &&
                thirdOptionVisible(state) &&
                !state.capture.isSelfTimerRunning(),
            icon = when {
                takesStills -> R.drawable.camera_shutter
                else -> R.drawable.option_circle
            },
            description = when {
                takesStills -> R.string.capture
                else -> R.string.open_gallery
            },
            thumbnail = state.gallery.thumbnail,
            thumbnailLoaderVisible = state.capture.isSavingPicture,
        )
    }

    private fun selfTimer(state: ViewfinderState): SelfTimerUiState {
        if (state.isQrMode()) return SelfTimerUiState()

        val duration = state.settings.selfTimerDuration

        return SelfTimerUiState(
            remaining = state.capture.selfTimerRemaining,
            badge = when (duration) {
                Duration.ZERO -> ""
                else -> "${duration.inWholeSeconds}s"
            },
            badgeVisible = duration != Duration.ZERO &&
                !state.isVideoMode() &&
                !state.capture.isSelfTimerRunning(),
        )
    }

    private fun recording(state: ViewfinderState): RecordingUiState {
        val recording = state.recording
        val includeAudio = state.settings.includeAudio

        return RecordingUiState(
            isActive = recording.isActive(),
            isMuted = recording.isMuted,
            muteToggleVisible = recording.isRecording() && includeAudio,
            micOffIconVisible = !state.isQrMode() && state.isVideoMode() && !includeAudio,
            timerVisible = !state.isQrMode() && recording.isRecording(),
            timerText = formatVideoDuration(recording.duration.inWholeSeconds),
        )
    }

    private fun exposure(state: ViewfinderState): ExposureUiState {
        val exposure = state.session.exposure

        return ExposureUiState(
            range = exposure?.compensationRange,
            progress = exposure?.compensationIndex ?: 0,
            panelVisible = state.previewControls.isExposurePanelShown,
        )
    }

    private fun rotation(state: ViewfinderState): RotationUiState {
        val deviceRotation = deviceRotationDegrees(state)

        return RotationUiState(
            iconDegrees = when {
                state.autoRotateEnabled -> deviceRotation
                else -> 0f
            },
            levelFrameDegrees = deviceRotation,
        )
    }

    private fun deviceRotationDegrees(state: ViewfinderState): Float {
        val orientation = state.recording.orientationAtStart
            ?: state.deviceOrientation
            ?: return 0f
        val displayDegrees = displayDegrees(state.displayRotation)

        return (displayDegrees - orientation.degrees).mod(FULL_TURN_DEGREES).toFloat()
    }

    @Suppress("MagicNumber")
    private fun displayDegrees(displayRotation: Int): Int {
        return when (displayRotation) {
            Surface.ROTATION_90 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 90
            else -> 0
        }
    }

    private fun modeTabsVisible(state: ViewfinderState): Boolean {
        return state.showsCameraModeTabs &&
            !state.recording.isRecording() &&
            !state.capture.isSelfTimerRunning()
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

    private companion object {
        private const val FULL_TURN_DEGREES = 360
    }
}
