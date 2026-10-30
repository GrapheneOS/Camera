package app.grapheneos.camera.ui.statusbar.screen

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.grapheneos.camera.ui.camerasettings.navigation.openCameraSettings
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarScreenEffect as Effect

@Composable
internal fun rememberStatusBarEffectHandler(): StatusBarEffectHandler {
    val activity = requireNotNull(LocalActivity.current) {
        "The status bar needs a hosting activity"
    }

    return remember(activity) {
        StatusBarEffectHandlerImpl(activity)
    }
}

internal interface StatusBarEffectHandler {
    fun handle(effect: Effect)
}

internal class StatusBarEffectHandlerImpl(
    private val activity: Activity,
) : StatusBarEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.OpenCameraSettings -> openCameraSettings(
                activity = activity,
                entryPoint = effect.entryPoint,
                capabilities = effect.capabilities,
            )
        }
    }
}
