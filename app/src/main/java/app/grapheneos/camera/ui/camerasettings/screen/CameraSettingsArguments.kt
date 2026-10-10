package app.grapheneos.camera.ui.camerasettings.screen

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.PhotoResolutions

private const val IS_ZSL_SUPPORTED = "camera_settings_is_zsl_supported"
private const val IS_EIS_SUPPORTED = "camera_settings_is_eis_supported"
private const val STANDARD_MEGAPIXELS = "camera_settings_standard_megapixels"
private const val FULL_MEGAPIXELS = "camera_settings_full_megapixels"

internal fun cameraSettingsArguments(capabilities: CameraCapabilities): Bundle {
    val resolutions = capabilities.photoResolutions

    return Bundle().apply {
        putBoolean(IS_ZSL_SUPPORTED, capabilities.isZslSupported)
        putBoolean(IS_EIS_SUPPORTED, capabilities.isVideoStabilizationSupported)
        if (resolutions != null) {
            putInt(STANDARD_MEGAPIXELS, resolutions.standardMegapixels)
            putInt(FULL_MEGAPIXELS, resolutions.fullMegapixels)
        }
    }
}

internal fun cameraCapabilitiesOf(arguments: SavedStateHandle): CameraCapabilities {
    return CameraCapabilities(
        isZslSupported = arguments.get<Boolean>(IS_ZSL_SUPPORTED) ?: false,
        isVideoStabilizationSupported = arguments.get<Boolean>(IS_EIS_SUPPORTED) ?: false,
        photoResolutions = photoResolutionsOf(arguments),
    )
}

private fun photoResolutionsOf(arguments: SavedStateHandle): PhotoResolutions? {
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
