package app.grapheneos.camera.ui.camerasettings.screen

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.camera.model.CameraCapabilities

private const val CAPABILITIES = "camera_settings_capabilities"

internal fun cameraSettingsArguments(capabilities: CameraCapabilities): Bundle {
    return Bundle().apply {
        putParcelable(CAPABILITIES, capabilities)
    }
}

internal fun cameraCapabilitiesOf(arguments: SavedStateHandle): CameraCapabilities {
    return arguments.get<CameraCapabilities>(CAPABILITIES) ?: CameraCapabilities()
}
