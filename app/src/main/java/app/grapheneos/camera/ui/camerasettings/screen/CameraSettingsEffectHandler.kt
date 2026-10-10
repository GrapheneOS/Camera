package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect

@Composable
internal fun rememberCameraSettingsEffectHandler(
    storageLocationPicker: ActivityResultLauncher<Uri?>,
): CameraSettingsEffectHandler {
    return remember(storageLocationPicker) {
        CameraSettingsEffectHandlerImpl(storageLocationPicker = storageLocationPicker)
    }
}

internal interface CameraSettingsEffectHandler {
    fun handle(effect: Effect)
}

internal class CameraSettingsEffectHandlerImpl(
    private val storageLocationPicker: ActivityResultLauncher<Uri?>,
) : CameraSettingsEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.PickStorageLocation -> storageLocationPicker.launch(null)
        }
    }
}
