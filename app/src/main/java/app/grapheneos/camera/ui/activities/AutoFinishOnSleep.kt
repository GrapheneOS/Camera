package app.grapheneos.camera.ui.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Finishes the passed [activity] and the ones present below it in the stack if the screen
// turns off
class AutoFinishOnSleep(val activity: Activity) {

    companion object {
        private const val TAG = "AutoFinishOnSleep"

        private val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!activity.isFinishing) {
                        ActivityCompat.finishAffinity(activity)
                    }
                }
            }
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            activity,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun stop() {
        activity.unregisterReceiver(receiver)
    }
}
