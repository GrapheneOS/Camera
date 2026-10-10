package app.grapheneos.camera.ui.camerasettings.screen

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

private const val IS_ZSL_SUPPORTED = "camera_settings_is_zsl_supported"

internal fun cameraSettingsArguments(isZslSupported: Boolean): Bundle {
    return Bundle().apply {
        putBoolean(IS_ZSL_SUPPORTED, isZslSupported)
    }
}

internal fun isZslSupportedOf(arguments: SavedStateHandle): Boolean {
    return arguments.get<Boolean>(IS_ZSL_SUPPORTED) ?: false
}
