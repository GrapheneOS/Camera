package app.grapheneos.camera.ui.camerasettings.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
    val activity = requireNotNull(LocalActivity.current) {
        "Camera settings need a hosting activity"
    }

    return remember(activity, locationPermissionRequest, storageLocationPicker) {
        CameraSettingsEffectHandlerImpl(
            activity = activity,
            locationPermissionRequest = locationPermissionRequest,
            storageLocationPicker = storageLocationPicker,
        )
    }
}

internal interface CameraSettingsEffectHandler {
    fun handle(effect: Effect)
}

internal class CameraSettingsEffectHandlerImpl(
    private val activity: Activity,
    private val locationPermissionRequest: ActivityResultLauncher<Array<String>>,
    private val storageLocationPicker: ActivityResultLauncher<Uri?>,
) : CameraSettingsEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.RequestLocationPermission -> requestLocationPermission()
            is Effect.OpenAppSettings -> openAppSettings()
            is Effect.PickStorageLocation -> storageLocationPicker.launch(null)
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(AppPermission.LOCATION.manifestNames.toTypedArray())
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))

        activity.startActivity(intent)
    }
}
