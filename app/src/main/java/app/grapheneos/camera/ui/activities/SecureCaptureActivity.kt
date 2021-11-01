package app.grapheneos.camera.ui.activities

import android.os.Bundle

class SecureCaptureActivity : CaptureActivity() {

    var openedActivityAt = SecureMainActivity.DEFAULT_OPENED_AT_TIMESTAMP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedActivityAt = System.currentTimeMillis()
    }

}