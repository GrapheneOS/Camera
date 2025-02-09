package app.grapheneos.camera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.ActivityCompat

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
                    ActivityCompat.finishAffinity(activity)
                }
            }
        }
    }

    fun start() {
        activity.registerReceiver(receiver, intentFilter)
    }

    fun stop() {
        activity.unregisterReceiver(receiver)
    }
}
