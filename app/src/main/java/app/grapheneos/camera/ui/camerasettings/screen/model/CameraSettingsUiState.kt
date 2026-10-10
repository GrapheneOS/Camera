package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class CameraSettingsUiState(
    val showsSecureSessionBanner: Boolean = false,
    val removeExif: SwitchSettingUiModel = SwitchSettingUiModel(),
    val gyroscopeSuggestions: SwitchSettingUiModel = SwitchSettingUiModel(),
    val saveImageAsPreviewed: SwitchSettingUiModel = SwitchSettingUiModel(),
    val saveVideoAsPreviewed: SwitchSettingUiModel = SwitchSettingUiModel(),
    val photoQuality: PhotoQualityUiModel = PhotoQualityUiModel(),
    val selectHighestResolution: SwitchSettingUiModel = SwitchSettingUiModel(),
    val zsl: SwitchSettingUiModel? = null,
    val cameraSounds: SwitchSettingUiModel = SwitchSettingUiModel(),
    val storageLocation: StorageLocationUiModel? = null,
    val dialog: CameraSettingsDialog? = null,
)
