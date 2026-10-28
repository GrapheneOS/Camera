package app.grapheneos.camera.ui.camerasettings.navigation

import android.app.Activity
import android.content.Intent
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.di.core.cameraEntryPointArguments
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.camerasettings.CameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.SecureCameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.screen.cameraSettingsArguments
import app.grapheneos.camera.ui.core.activity.SecureActivity

internal fun openCameraSettings(
    activity: Activity,
    entryPoint: CameraEntryPoint,
    capabilities: CameraCapabilities,
) {
    val screen = when (activity) {
        is SecureActivity -> SecureCameraSettingsActivity::class.java
        else -> CameraSettingsActivity::class.java
    }
    val intent = Intent(activity, screen)
        .putExtras(cameraEntryPointArguments(entryPoint))
        .putExtras(cameraSettingsArguments(capabilities))

    activity.startActivity(intent)
}
