package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
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
            saveImageAsPreviewed = switchSetting(
                checked = settings.saveImageAsPreviewed,
                default = SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
            ),
            saveVideoAsPreviewed = switchSetting(
                checked = settings.saveVideoAsPreviewed,
                default = SettingsDefaults.SAVE_VIDEO_AS_PREVIEW,
            ),
            photoQuality = photoQuality(settings.photoQuality),
            selectHighestResolution = switchSetting(
                checked = settings.selectHighestResolution,
                default = SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
            ),
            zsl = switchSetting(
                checked = settings.enableZsl,
                default = SettingsDefaults.ENABLE_ZSL,
            ).takeIf { state.isZslSupported },
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

    private fun photoQuality(quality: Int): PhotoQualityUiModel {
        return PhotoQualityUiModel(
            quality = quality,
            level = PhotoQualityLevel.of(quality),
            isChanged = quality != SettingsDefaults.PHOTO_QUALITY,
            levels = PhotoQualityLevel.entries.toImmutableList(),
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
