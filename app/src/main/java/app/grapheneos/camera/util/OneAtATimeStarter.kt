package app.grapheneos.camera.util

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Starts activities from [activity] one at a time. Its window takes taps until the started
 * activity shows, so a quick second tap used to stack a second copy on top of the first.
 */
class OneAtATimeStarter(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    private var isStarting = false

    init {
        activity.lifecycle.addObserver(this)
    }

    fun start(intent: Intent) {
        if (isStarting) {
            return
        }
        isStarting = true
        activity.startActivity(intent)
    }

    // Not onPause: taps still arrive after it, until the started activity covers the window
    override fun onResume(owner: LifecycleOwner) {
        isStarting = false
    }
}
