package app.grapheneos.camera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat

// Finishes the passed [activity] and the ones present below it in the stack if the screen
// turns off and the user is inactive for [WAITING_TIME_IN_MS] milliseconds
class AutoFinishOnSleep(val activity: Activity) {

    companion object {
        private const val TAG = "AutoFinishOnSleep"
        private const val WAITING_TIME_IN_MS = 1500L

        private val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val runnable = Runnable {
        ActivityCompat.finishAffinity(activity)
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.postDelayed(runnable, WAITING_TIME_IN_MS)
                }

                Intent.ACTION_SCREEN_ON -> {
                    handler.removeCallbacks(runnable)
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
