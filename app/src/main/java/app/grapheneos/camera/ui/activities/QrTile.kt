package app.grapheneos.camera.ui.activities

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.core.content.getSystemService
import app.grapheneos.camera.CameraMode

// Keep this activity name for existing SystemUI QR shortcuts.
class QrTile : SecureMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camConfig.switchMode(CameraMode.QR_SCAN)
    }

    override fun shouldShowCameraModeTabs() = false

    override fun startActivity(intent: Intent, options: Bundle?) {
        val keyguardManager = getSystemService<KeyguardManager>()!!

        if (keyguardManager.isKeyguardLocked) {
            val cb = object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super@QrTile.startActivity(intent, options);
                }
            }
            keyguardManager.requestDismissKeyguard(this, cb)
        } else {
            super.startActivity(intent, options)
        }
    }
}
