package app.grapheneos.camera.ui.camerasettings.navigation

import android.app.Activity
import android.content.Intent
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.di.core.cameraEntryPointArguments
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.camerasettings.CameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.LicensesActivity
import app.grapheneos.camera.ui.camerasettings.SecureCameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.screen.cameraSettingsArguments
import app.grapheneos.camera.ui.core.activity.SecureActivity
import javax.inject.Inject

internal interface CameraSettingsIntents {

    fun open(
        activity: Activity,
        entryPoint: CameraEntryPoint,
        capabilities: CameraCapabilities,
    )

    fun openLicenses(activity: Activity)
}

internal class CameraSettingsIntentsImpl @Inject constructor() : CameraSettingsIntents {

    override fun open(
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

    override fun openLicenses(activity: Activity) {
        activity.startActivity(Intent(activity, LicensesActivity::class.java))
    }
}
