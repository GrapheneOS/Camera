package app.grapheneos.camera.ui.activities

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.core.content.getSystemService
import app.grapheneos.camera.CameraMode

// Requires integration into the OS, see config_defaultQrCodeComponent in frameworks/base.
//
// SystemUI links this activity via a quick tile or a lockscreen shortcut.
// The activity name is historical, changing it would break the SystemUI integration for users on
// older OS versions.
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
