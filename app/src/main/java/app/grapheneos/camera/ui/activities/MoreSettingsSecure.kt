package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.AutoFinishOnSleep

class MoreSettingsSecure : MoreSettings() {

    private val autoFinisher = AutoFinishOnSleep(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoFinisher.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoFinisher.stop()
    }
}
