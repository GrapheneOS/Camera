package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.ChoiceSettingUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.GridOption
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
            geoTagging = geoTagging(state),
            removeExif = removeExif(state),
            grid = grid(state),
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
            videoStabilization = videoStabilization(state),
            cameraSounds = switchSetting(
                checked = settings.enableCameraSounds,
                default = SettingsDefaults.CAMERA_SOUNDS,
            ),
            focusTimeout = focusTimeout(state),
            storageLocation = storageLocation(state),
            canResetSettings = state.settingsAfterReset() != settings,
            dialog = state.dialog,
        )
    }

    private fun geoTagging(state: CameraSettingsState): SwitchSettingUiModel {
        val setting = switchSetting(
            checked = state.settings.geoTagging,
            default = SettingsDefaults.GEO_TAGGING,
        )

        return setting.copy(enabled = state.isLocationPermissionGranted || !state.isSecureSession)
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

    private fun grid(state: CameraSettingsState): ChoiceSettingUiModel<GridOption> {
        val selected = GridOption.of(state.settings.gridType)

        return ChoiceSettingUiModel(
            selected = selected,
            options = GridOption.entries.toImmutableList(),
            isChanged = selected.type != SettingsDefaults.GRID_TYPE,
        )
    }

    private fun photoQuality(state: CameraSettingsState): PhotoQualityUiModel {
        val quality = state.settings.photoQuality
        val isFullResolution = state.settings.selectHighestResolution
        val resolution = state.capabilities.photoResolutions?.let { resolutions ->
            PhotoResolutionUiModel(
                standardMegapixels = resolutions.standardMegapixels,
                fullMegapixels = resolutions.fullMegapixels,
            )
        }

        return PhotoQualityUiModel(
            quality = quality,
            level = PhotoQualityLevel.of(quality),
            isFullResolution = isFullResolution,
            resolution = resolution,
            isChanged = quality != SettingsDefaults.PHOTO_QUALITY || isFullResolution,
            presets = PhotoQualityLevel.presets().toImmutableList(),
        )
    }

    private fun captureMode(state: CameraSettingsState): ChoiceSettingUiModel<CaptureModeOption> {
        val selected = CaptureModeOption.of(state.settings.captureMode)
        val options = CaptureModeOption.entries.filter { option ->
            option != CaptureModeOption.ZERO_SHUTTER_LAG ||
                state.capabilities.isZslSupported ||
                option == selected
        }

        return ChoiceSettingUiModel(
            selected = selected,
            options = options.toImmutableList(),
            isChanged = selected.mode != SettingsDefaults.CAPTURE_MODE,
        )
    }

    private fun videoStabilization(state: CameraSettingsState): SwitchSettingUiModel {
        val setting = switchSetting(
            checked = state.settings.enableEis,
            default = SettingsDefaults.ENABLE_EIS,
        )

        return setting.copy(enabled = state.capabilities.isVideoStabilizationSupported)
    }

    private fun focusTimeout(state: CameraSettingsState): ChoiceSettingUiModel<Long> {
        val selected = state.settings.focusTimeoutSeconds
        val options = (FOCUS_TIMEOUT_OPTIONS + selected).distinct().sorted()

        return ChoiceSettingUiModel(
            selected = selected,
            options = options.toImmutableList(),
            isChanged = selected != SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
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

    private companion object {
        val FOCUS_TIMEOUT_OPTIONS = listOf(0L, 3L, 5L, 8L, 10L)
    }
}
