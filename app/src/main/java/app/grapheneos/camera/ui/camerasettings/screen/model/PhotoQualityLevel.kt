package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.R

internal enum class PhotoQualityLevel(
    val quality: Int,
    @StringRes val label: Int,
) {
    MAXIMUM(
        quality = 100,
        label = R.string.camera_settings_photo_quality_maximum,
    ),
    HIGH(
        quality = 95,
        label = R.string.camera_settings_photo_quality_high,
    ),
    STANDARD(
        quality = 90,
        label = R.string.camera_settings_photo_quality_standard,
    ),
    COMPACT(
        quality = 75,
        label = R.string.camera_settings_photo_quality_compact,
    ),
    ;

    companion object {
        fun of(quality: Int): PhotoQualityLevel? {
            return entries.find { it.quality == quality }
        }
    }
}
