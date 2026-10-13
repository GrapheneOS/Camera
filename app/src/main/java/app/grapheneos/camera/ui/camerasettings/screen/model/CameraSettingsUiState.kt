package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class CameraSettingsUiState(
    val showsSecureSessionBanner: Boolean = false,
    val geoTagging: SwitchSettingUiModel = SwitchSettingUiModel(),
    val offersLocationSettings: Boolean = false,
    val removeExif: SwitchSettingUiModel = SwitchSettingUiModel(),
    val grid: ChoiceSettingUiModel<GridOption> = ChoiceSettingUiModel(
        selected = GridOption.of(SettingsDefaults.GRID_TYPE),
        options = persistentListOf(),
    ),
    val gyroscopeSuggestions: SwitchSettingUiModel = SwitchSettingUiModel(),
    val keepSelfiesAsSeen: SwitchSettingUiModel = SwitchSettingUiModel(),
    val photoQuality: PhotoQualityUiModel = PhotoQualityUiModel(),
    val captureMode: ChoiceSettingUiModel<CaptureModeOption> = ChoiceSettingUiModel(
        selected = CaptureModeOption.of(SettingsDefaults.CAPTURE_MODE),
        options = persistentListOf(),
    ),
    val videoStabilization: SwitchSettingUiModel = SwitchSettingUiModel(),
    val cameraSounds: SwitchSettingUiModel = SwitchSettingUiModel(),
    val focusTimeout: ChoiceSettingUiModel<Long> = ChoiceSettingUiModel(
        selected = SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
        options = persistentListOf(),
    ),
    val storageSpace: StorageSpaceUiModel? = null,
    val storageLocation: StorageLocationUiModel? = null,
    val showsLicenses: Boolean = false,
    val canResetSettings: Boolean = false,
    val dialog: CameraSettingsDialog? = null,
)
