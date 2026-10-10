package app.grapheneos.camera.ui.camerasettings.screen

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.camera.model.PhotoResolutions

private const val IS_ZSL_SUPPORTED = "camera_settings_is_zsl_supported"
private const val STANDARD_MEGAPIXELS = "camera_settings_standard_megapixels"
private const val FULL_MEGAPIXELS = "camera_settings_full_megapixels"

internal fun cameraSettingsArguments(
    isZslSupported: Boolean,
    photoResolutions: PhotoResolutions?,
): Bundle {
    return Bundle().apply {
        putBoolean(IS_ZSL_SUPPORTED, isZslSupported)
        if (photoResolutions != null) {
            putInt(STANDARD_MEGAPIXELS, photoResolutions.standardMegapixels)
            putInt(FULL_MEGAPIXELS, photoResolutions.fullMegapixels)
        }
    }
}

internal fun isZslSupportedOf(arguments: SavedStateHandle): Boolean {
    return arguments.get<Boolean>(IS_ZSL_SUPPORTED) ?: false
}

internal fun photoResolutionsOf(arguments: SavedStateHandle): PhotoResolutions? {
    val standard = arguments.get<Int>(STANDARD_MEGAPIXELS)
    val full = arguments.get<Int>(FULL_MEGAPIXELS)

    return when {
        standard == null || full == null -> null
        else -> PhotoResolutions(
            standardMegapixels = standard,
            fullMegapixels = full,
        )
    }
}
