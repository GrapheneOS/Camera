package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
internal data class PhotoQualityUiModel(
    val quality: Int = SettingsDefaults.PHOTO_QUALITY,
    val level: PhotoQualityLevel? = PhotoQualityLevel.of(SettingsDefaults.PHOTO_QUALITY),
    val isChanged: Boolean = false,
    val levels: ImmutableList<PhotoQualityLevel> = PhotoQualityLevel.entries.toImmutableList(),
)
