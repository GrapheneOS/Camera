package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect

@Composable
internal fun rememberCameraSettingsEffectHandler(
    locationPermissionRequest: ActivityResultLauncher<Array<String>>,
    storageLocationPicker: ActivityResultLauncher<Uri?>,
): CameraSettingsEffectHandler {
    return remember(locationPermissionRequest, storageLocationPicker) {
        CameraSettingsEffectHandlerImpl(
            locationPermissionRequest = locationPermissionRequest,
            storageLocationPicker = storageLocationPicker,
        )
    }
}

internal interface CameraSettingsEffectHandler {
    fun handle(effect: Effect)
}

internal class CameraSettingsEffectHandlerImpl(
    private val locationPermissionRequest: ActivityResultLauncher<Array<String>>,
    private val storageLocationPicker: ActivityResultLauncher<Uri?>,
) : CameraSettingsEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.RequestLocationPermission -> requestLocationPermission()
            is Effect.PickStorageLocation -> storageLocationPicker.launch(null)
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(AppPermission.LOCATION.manifestNames.toTypedArray())
    }
}
