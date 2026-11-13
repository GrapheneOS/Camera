package app.grapheneos.camera.ui.quicksettings.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.navigation.openCameraSettings
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect

@Composable
internal fun rememberQuickSettingsEffectHandler(): QuickSettingsEffectHandler {
    val activity = requireNotNull(LocalActivity.current) {
        "Quick settings need a hosting activity"
    }

    return remember(activity) {
        QuickSettingsEffectHandlerImpl(activity)
    }
}

internal interface QuickSettingsEffectHandler {
    fun handle(effect: Effect)
}

internal class QuickSettingsEffectHandlerImpl(
    private val activity: Activity,
) : QuickSettingsEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.OpenCameraSettings -> openCameraSettings(
                activity = activity,
                entryPoint = effect.entryPoint,
                capabilities = effect.capabilities,
            )

            is Effect.TextCopied -> showTextCopied()
        }
    }

    private fun showTextCopied() {
        Toast.makeText(activity, R.string.copied_text_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
