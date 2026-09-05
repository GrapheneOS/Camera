package app.grapheneos.camera.ui

import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode

@StringRes
fun cameraModeLabel(mode: CameraMode): Int {
    return when (mode) {
        CameraMode.QR_SCAN -> R.string.qr_scan_mode
        CameraMode.AUTO -> R.string.auto_mode
        CameraMode.FACE_RETOUCH -> R.string.face_retouch_mode
        CameraMode.PORTRAIT -> R.string.portrait_mode
        CameraMode.NIGHT -> R.string.night_mode
        CameraMode.HDR -> R.string.hdr_mode
        CameraMode.CAMERA -> R.string.camera
        CameraMode.VIDEO -> R.string.video
    }
}
