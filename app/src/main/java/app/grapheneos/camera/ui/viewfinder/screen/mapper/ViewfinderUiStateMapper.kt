package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import javax.inject.Inject

interface ViewfinderUiStateMapper {

    fun map(
        mode: CameraMode,
        isVideoMode: Boolean,
        flashMode: Int,
        requireLocation: Boolean,
        settings: CameraSettings,
        modeSettings: ModeSettings,
        session: ViewfinderSessionState,
    ): ViewfinderUiState
}

internal class ViewfinderUiStateMapperImpl @Inject constructor(
    private val settingsSheetUiStateMapper: SettingsSheetUiStateMapper,
) : ViewfinderUiStateMapper {

    override fun map(
        mode: CameraMode,
        isVideoMode: Boolean,
        flashMode: Int,
        requireLocation: Boolean,
        settings: CameraSettings,
        modeSettings: ModeSettings,
        session: ViewfinderSessionState,
    ): ViewfinderUiState {
        val chrome = when {
            mode.isQr -> qrState(settings)

            else -> captureState(
                isVideoMode = isVideoMode,
                settings = settings,
            )
        }

        return chrome.copy(
            settingsSheet = settingsSheetUiStateMapper.map(
                isVideoMode = isVideoMode,
                flashMode = flashMode,
                requireLocation = requireLocation,
                settings = settings,
                modeSettings = modeSettings,
                session = session,
            ),
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
