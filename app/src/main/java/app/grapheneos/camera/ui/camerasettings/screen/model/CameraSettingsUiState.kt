package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class CameraSettingsUiState(
    val showsSecureSessionBanner: Boolean = false,
    val removeExif: SwitchSettingUiModel = SwitchSettingUiModel(),
    val gyroscopeSuggestions: SwitchSettingUiModel = SwitchSettingUiModel(),
    val keepSelfiesAsSeen: SwitchSettingUiModel = SwitchSettingUiModel(),
    val photoQuality: PhotoQualityUiModel = PhotoQualityUiModel(),
    val captureMode: CaptureModeUiModel = CaptureModeUiModel(),
    val cameraSounds: SwitchSettingUiModel = SwitchSettingUiModel(),
    val storageLocation: StorageLocationUiModel? = null,
    val dialog: CameraSettingsDialog? = null,
)
