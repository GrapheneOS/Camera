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
    CUSTOM(
        quality = 75,
        label = R.string.camera_settings_photo_quality_custom,
    ),
    ;

    companion object {
        fun presets(): List<PhotoQualityLevel> {
            return entries.filter { it != CUSTOM }
        }

        fun of(quality: Int): PhotoQualityLevel {
            return entries.find { it != CUSTOM && it.quality == quality } ?: CUSTOM
        }
    }
}
