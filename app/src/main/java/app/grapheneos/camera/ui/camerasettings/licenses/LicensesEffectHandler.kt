package app.grapheneos.camera.ui.camerasettings.licenses

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesScreenEffect as Effect

@Composable
internal fun rememberLicensesEffectHandler(): LicensesEffectHandler {
    val activity = requireNotNull(LocalActivity.current) {
        "Licenses need a hosting activity"
    }

    return remember(activity) {
        LicensesEffectHandlerImpl(activity = activity)
    }
}

internal interface LicensesEffectHandler {
    fun handle(effect: Effect)
}

internal class LicensesEffectHandlerImpl(
    private val activity: Activity,
) : LicensesEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.OpenUrl -> openUrl(effect.url)
        }
    }

    private fun openUrl(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "No app opens $url", exception)
        }
    }

    private companion object {
        const val TAG = "LicensesEffectHandler"
    }
}
