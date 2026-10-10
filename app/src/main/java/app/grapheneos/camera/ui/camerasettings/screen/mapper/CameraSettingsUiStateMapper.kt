package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageLocationUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

internal interface CameraSettingsUiStateMapper {
    fun map(state: CameraSettingsState): CameraSettingsUiState
}

internal class CameraSettingsUiStateMapperImpl @Inject constructor() : CameraSettingsUiStateMapper {

    override fun map(state: CameraSettingsState): CameraSettingsUiState {
        val settings = state.settings

        return CameraSettingsUiState(
            showsSecureSessionBanner = state.isSecureSession,
            removeExif = removeExif(state),
            gyroscopeSuggestions = switchSetting(
                checked = settings.gyroscopeSuggestions,
                default = SettingsDefaults.GYROSCOPE_SUGGESTIONS,
            ),
            keepSelfiesAsSeen = switchSetting(
                checked = settings.keepSelfiesAsSeen,
                default = SettingsDefaults.KEEP_SELFIES_AS_SEEN,
            ),
            photoQuality = photoQuality(state),
            captureMode = captureMode(state),
            cameraSounds = switchSetting(
                checked = settings.enableCameraSounds,
                default = SettingsDefaults.CAMERA_SOUNDS,
            ),
            storageLocation = storageLocation(state),
            dialog = state.dialog,
        )
    }

    private fun removeExif(state: CameraSettingsState): SwitchSettingUiModel {
        return when {
            state.isCaptureSession -> SwitchSettingUiModel(
                checked = true,
                enabled = false,
            )

            else -> switchSetting(
                checked = state.settings.removeExifAfterCapture,
                default = SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
            )
        }
    }

    private fun photoQuality(state: CameraSettingsState): PhotoQualityUiModel {
        val quality = state.settings.photoQuality
        val isFullResolution = state.settings.selectHighestResolution
        val resolution = state.photoResolutions?.let { resolutions ->
            PhotoResolutionUiModel(
                standardMegapixels = resolutions.standardMegapixels,
                fullMegapixels = resolutions.fullMegapixels,
            )
        }
        val isResolutionChanged = resolution != null &&
            isFullResolution != SettingsDefaults.SELECT_HIGHEST_RESOLUTION

        return PhotoQualityUiModel(
            quality = quality,
            level = PhotoQualityLevel.of(quality),
            isFullResolution = isFullResolution,
            resolution = resolution,
            isChanged = quality != SettingsDefaults.PHOTO_QUALITY || isResolutionChanged,
            presets = PhotoQualityLevel.presets().toImmutableList(),
        )
    }

    private fun captureMode(state: CameraSettingsState): CaptureModeUiModel {
        val selected = CaptureModeOption.of(state.settings.captureMode)
        val options = CaptureModeOption.entries.filter { option ->
            option != CaptureModeOption.ZERO_SHUTTER_LAG ||
                state.isZslSupported ||
                option == selected
        }

        return CaptureModeUiModel(
            selected = selected,
            options = options.toImmutableList(),
            isChanged = selected.mode != SettingsDefaults.CAPTURE_MODE,
        )
    }

    private fun storageLocation(state: CameraSettingsState): StorageLocationUiModel? {
        val location = state.storageLocation.takeUnless { state.isSecureSession } ?: return null

        return StorageLocationUiModel(
            location = location,
            isChanged = location != CapturedItemRepository.MEDIA_STORE_LOCATION,
        )
    }

    private fun switchSetting(
        checked: Boolean,
        default: Boolean,
    ): SwitchSettingUiModel {
        return SwitchSettingUiModel(
            checked = checked,
            isChanged = checked != default,
        )
    }
}
