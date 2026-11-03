package app.grapheneos.camera.ui.viewfinder.screen.mapper

import android.view.Surface
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderLevelState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureButtonUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureResultUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ExposureUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FlipCameraUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FocusRingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ModeTabsUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.PreviewUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.QrUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RecordingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RotationUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.SelfTimerUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ThirdOptionUiModel
import app.grapheneos.camera.util.formatVideoDuration
import javax.inject.Inject
import kotlin.time.Duration

interface ViewfinderUiStateMapper {
    fun map(state: ViewfinderState): ViewfinderUiState

    fun mapZoom(state: ViewfinderState): ZoomUiState

    fun mapLevel(state: ViewfinderLevelState): LevelUiState
}

internal class ViewfinderUiStateMapperImpl @Inject constructor() : ViewfinderUiStateMapper {

    override fun map(state: ViewfinderState): ViewfinderUiState {
        return ViewfinderUiState(
            mode = state.mode,
            preview = preview(state),
            modeTabs = modeTabs(state),
            captureButton = captureButton(state),
            flipCamera = flipCamera(state),
            thirdOption = thirdOption(state),
            cancelButtonVisible = cancelButtonVisible(state),
            selfTimer = selfTimer(state),
            recording = recording(state),
            qr = qr(state),
            focusRing = focusRing(state),
            exposure = exposure(state),
            rotation = rotation(state),
            captureResult = captureResult(state),
            keepScreenOn = keepScreenOn(state),
            isSelfIlluminating = state.selfIlluminates(),
            isSettingsPanelAvailable = state.isSettingsPanelAvailable(),
            handlesVolumeKeys = state.settings.volumeKeyAction != VolumeKeyAction.VOLUME,
            dialog = state.dialog,
        )
    }

    override fun mapZoom(state: ViewfinderState): ZoomUiState {
        val zoom = state.session.zoom
        val panelVisible = state.previewControls.isZoomPanelShown

        return when (zoom) {
            null -> ZoomUiState(panelVisible = panelVisible)
            else -> ZoomUiState(
                zoomRatio = zoom.zoomRatio,
                linearZoom = zoom.linearZoom,
                panelVisible = panelVisible,
            )
        }
    }

    override fun mapLevel(state: ViewfinderLevelState): LevelUiState {
        return LevelUiState(
            visible = state.visible,
            tiltDegrees = state.tiltDegrees,
            horizonDegrees = state.horizonDegrees,
        )
    }

    private fun preview(state: ViewfinderState): PreviewUiModel {
        return PreviewUiModel(
            aspectRatio = state.aspectRatio(),
            sensorOrientationDegrees = state.session.sensorOrientationDegrees,
            gridType = state.settings.gridType,
            isStreaming = state.preview.isStreaming,
            lastFrameVisible = state.preview.isLastFrameShown,
            blursLastFrame = !state.isCaptureSession,
        )
    }

    private fun modeTabs(state: ViewfinderState): ModeTabsUiModel {
        return ModeTabsUiModel(
            visible = modeTabsVisible(state),
            availableModes = state.session.availableModes,
            selectedMode = state.modeStripTarget(),
            selectionId = state.modeStrip.selectionId,
        )
    }

    private fun modeTabsVisible(state: ViewfinderState): Boolean {
        return state.showsCameraModeTabs &&
            !state.recording.isRecording() &&
            !state.capture.isSelfTimerRunning()
    }

    private fun captureButton(state: ViewfinderState): CaptureButtonUiModel {
        val isTorchOn = state.session.isTorchOn
        val common = CaptureButtonUiModel(
            visible = !state.capture.hasResult() && !state.capture.isShutterHeld,
            enabled = !state.capture.isTakingPicture && !state.capture.isShutterHeld,
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
                description = captureButtonDescription(state),
            )
        }
    }

    private fun captureButtonDescription(state: ViewfinderState): Int {
        return when {
            state.recording.isRecording() -> R.string.stop_recording
            state.isVideoMode() -> R.string.start_recording
            // The cross is drawn over the button, but accessibility must announce cancellation.
            state.capture.isSelfTimerRunning() -> R.string.cancel_timer
            else -> R.string.capture
        }
    }

    private fun flipCamera(state: ViewfinderState): FlipCameraUiModel {
        val visible = !state.capture.isSelfTimerRunning()
        val button = when {
            state.isQrMode() -> scanAllCodesToggle(state.settings.scanAllCodes)

            state.recording.isRecording() -> recordingPauseToggle(
                paused = state.recording.isPaused,
                visible = visible,
            )

            else -> FlipCameraUiModel(visible = visible)
        }

        return button.copy(lensFacing = state.session.lensFacing)
    }

    private fun scanAllCodesToggle(scanAllCodes: Boolean): FlipCameraUiModel {
        return FlipCameraUiModel(
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
    ): FlipCameraUiModel {
        return FlipCameraUiModel(
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

    private fun thirdOption(state: ViewfinderState): ThirdOptionUiModel {
        // While recording, the gallery button turns into a shutter for stills
        val takesStills = !state.isQrMode() && state.recording.isRecording()

        return ThirdOptionUiModel(
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

    /**
     * The third circle opens the gallery, which a capture session must not reach; a video capture
     * session shows it only to play back the recording under review.
     */
    private fun thirdOptionVisible(state: ViewfinderState): Boolean {
        return when {
            state.isCaptureSession -> {
                state.requiresVideoModeOnly && state.capture.hasResult()
            }

            state.requiresVideoModeOnly -> !state.recording.isRecording()

            else -> true
        }
    }

    private fun cancelButtonVisible(state: ViewfinderState): Boolean {
        return !state.isQrMode() &&
            !state.capture.isSelfTimerRunning() &&
            !state.recording.isRecording()
    }

    private fun selfTimer(state: ViewfinderState): SelfTimerUiModel {
        if (state.isQrMode()) return SelfTimerUiModel()

        val duration = state.settings.selfTimerDuration

        return SelfTimerUiModel(
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

    private fun recording(state: ViewfinderState): RecordingUiModel {
        val recording = state.recording
        val includeAudio = state.settings.includeAudio

        return RecordingUiModel(
            isActive = recording.isActive(),
            isMuted = recording.isMuted,
            muteToggleVisible = recording.isRecording() && includeAudio,
            micOffIconVisible = !state.isQrMode() && state.isVideoMode() && !includeAudio,
            timerVisible = !state.isQrMode() && recording.isRecording(),
            timerText = formatVideoDuration(recording.duration.inWholeSeconds),
        )
    }

    private fun qr(state: ViewfinderState): QrUiModel {
        return QrUiModel(
            overlayVisible = state.isQrMode(),
            scanTogglesVisible = state.isQrMode() && !state.settings.scanAllCodes,
        )
    }

    private fun focusRing(state: ViewfinderState): FocusRingUiModel? {
        val request = state.previewControls.focusRequest ?: return null

        return FocusRingUiModel(
            x = request.x,
            y = request.y,
            id = request.id,
        )
    }

    private fun exposure(state: ViewfinderState): ExposureUiModel {
        val exposure = state.session.exposure

        return ExposureUiModel(
            range = exposure?.compensationRange,
            progress = exposure?.compensationIndex ?: 0,
            panelVisible = state.previewControls.isExposurePanelShown,
        )
    }

    private fun rotation(state: ViewfinderState): RotationUiModel {
        val deviceRotation = deviceRotationDegrees(state)

        return RotationUiModel(
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

    private fun captureResult(state: ViewfinderState): CaptureResultUiModel {
        return CaptureResultUiModel(
            visible = state.capture.hasResult(),
            image = state.capture.result?.image,
        )
    }

    private fun keepScreenOn(state: ViewfinderState): Boolean {
        return state.recording.isActive() || state.keepsScreenAwake
    }

    private companion object {
        private const val FULL_TURN_DEGREES = 360
    }
}
