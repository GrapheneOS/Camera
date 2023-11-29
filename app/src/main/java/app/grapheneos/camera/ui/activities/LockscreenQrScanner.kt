package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.CameraMode

class LockscreenQrScanner : SecureMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camConfig.switchMode(CameraMode.QR_SCAN)
        disableCameraModeSwitcher()
    }

}