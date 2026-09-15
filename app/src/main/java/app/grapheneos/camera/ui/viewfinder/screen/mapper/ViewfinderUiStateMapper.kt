package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
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
            state.isQrMode() -> qrState(settings)

            else -> captureState(
                isVideoMode = isVideoMode,
                settings = settings,
            )
        }

        return chrome.copy(
            gridType = settings.gridType,
            mode = state.mode,
            aspectRatio = state.aspectRatio(),
            isQrMode = state.isQrMode(),
            isVideoMode = isVideoMode,
            inPhotoMode = inPhotoMode,
            scanAllCodes = settings.scanAllCodes,
            focusTimeoutSeconds = settings.focusTimeoutSeconds,
            gyroscopeSuggestionsVisible = inPhotoMode && settings.gyroscopeSuggestions,
            settingsSheet = settingsSheetUiStateMapper.map(state),
            capture = captureUiStateMapper.map(state),
        )
    }

    private fun qrState(settings: CameraSettings): ViewfinderUiState {
        return ViewfinderUiState(
            qrOverlayVisible = true,
            qrScanTogglesVisible = !settings.scanAllCodes,
            thirdOptionVisible = false,
            cancelButtonVisible = false,
            micMutedIconVisible = false,
            captureButtonBackground = android.R.color.transparent,
            captureButtonIcon = R.drawable.torch_off_button,
            captureButtonDescription = R.string.turn_torch_on,
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
        isVideoMode: Boolean,
        settings: CameraSettings,
    ): ViewfinderUiState {
        val selfTimerSeconds = settings.selfTimerDurationSeconds

        return ViewfinderUiState(
            qrOverlayVisible = false,
            qrScanTogglesVisible = false,
            thirdOptionVisible = true,
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
}
