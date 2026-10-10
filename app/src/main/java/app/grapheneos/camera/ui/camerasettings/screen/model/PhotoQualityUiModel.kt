package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
internal data class PhotoQualityUiModel(
    val quality: Int = SettingsDefaults.PHOTO_QUALITY,
    val level: PhotoQualityLevel = PhotoQualityLevel.of(SettingsDefaults.PHOTO_QUALITY),
    val isFullResolution: Boolean = SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
    val resolution: PhotoResolutionUiModel? = null,
    val isChanged: Boolean = false,
    val presets: ImmutableList<PhotoQualityLevel> = PhotoQualityLevel.presets().toImmutableList(),
)
