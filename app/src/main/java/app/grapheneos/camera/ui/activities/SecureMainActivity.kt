package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.AutoFinishOnSleep
import app.grapheneos.camera.CapturedItem

open class SecureMainActivity : MainActivity(), SecureActivity {
    val capturedItems = ArrayList<CapturedItem>()

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
