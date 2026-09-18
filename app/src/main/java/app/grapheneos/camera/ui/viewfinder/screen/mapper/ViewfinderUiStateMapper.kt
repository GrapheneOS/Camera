package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
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
                capture = state.capture,
            )
        }

        return chrome.copy(
            captureButtonEnabled = !state.capture.isTakingPicture,
            thumbnailLoaderVisible = state.capture.isSavingPicture,
            capturedPreviewVisible = state.capture.isCapturedPreviewShown,
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
            captureButtonBackground = android.R.color.transparent,
            captureButtonIcon = when {
                isTorchOn -> R.drawable.torch_on_button
                else -> R.drawable.torch_off_button
            },
            captureButtonDescription = when {
                isTorchOn -> R.string.turn_torch_off
                else -> R.string.turn_torch_on
            },
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

        return ViewfinderUiState(
            qrOverlayVisible = false,
            qrScanTogglesVisible = false,
            thirdOptionVisible = thirdOptionVisible(state),
            cancelButtonVisible = true,
            micMutedIconVisible = isVideoMode && !settings.includeAudio,
            captureButtonBackground = R.drawable.cbutton_bg,
            captureButtonIcon = when {
                isVideoMode -> R.drawable.recording
                else -> R.drawable.camera_shutter
            },
            captureButtonDescription = when {
                isVideoMode -> R.string.start_recording
                else -> R.string.capture
            },
            flipCameraIcon = R.drawable.flip_camera,
            flipCameraDescription = R.string.flip_camera,
            selfTimerBadge = when (selfTimerSeconds) {
                0 -> ""
                else -> "${selfTimerSeconds}s"
            },
            selfTimerBadgeVisible = selfTimerSeconds != 0 && !isVideoMode,
        )
    }

    private fun withRecording(
        chrome: ViewfinderUiState,
        capture: ViewfinderCaptureState,
    ): ViewfinderUiState {
        if (!capture.isRecording) return chrome

        return chrome.copy(
            cancelButtonVisible = false,
            captureButtonDescription = R.string.stop_recording,
            flipCameraIcon = when {
                capture.isRecordingPaused -> R.drawable.play
                else -> R.drawable.pause
            },
            flipCameraDescription = when {
                capture.isRecordingPaused -> R.string.resume_recording
                else -> R.string.pause_recording
            },
        )
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

            state.requiresVideoModeOnly -> !state.capture.isRecording

            else -> true
        }
    }
}
