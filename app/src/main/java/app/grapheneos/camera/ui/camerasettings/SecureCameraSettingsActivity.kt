package app.grapheneos.camera.ui.camerasettings

import android.os.Bundle
import app.grapheneos.camera.ui.activities.AutoFinishOnSleep
import app.grapheneos.camera.ui.activities.SecureActivity

class SecureCameraSettingsActivity :
    CameraSettingsActivity(),
    SecureActivity {

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
