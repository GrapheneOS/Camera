package app.grapheneos.camera.ui.quicksettings.screen

import android.app.Activity
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.camerasettings.navigation.openCameraSettings
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect

internal interface QuickSettingsEffectHandler {
    fun handle(effect: Effect)
}

internal class QuickSettingsEffectHandlerImpl(
    private val activity: Activity,
    private val entryPoint: CameraEntryPoint,
    private val showMessage: (Int) -> Unit,
) : QuickSettingsEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> showMessage(effect.message)

            is Effect.OpenCameraSettings -> openCameraSettings(
                activity = activity,
                entryPoint = entryPoint,
                capabilities = effect.capabilities,
            )
        }
    }
}
