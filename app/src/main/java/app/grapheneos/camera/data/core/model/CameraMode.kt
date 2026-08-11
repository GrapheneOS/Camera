package app.grapheneos.camera.data.core.model

import androidx.camera.extensions.ExtensionMode

enum class CameraMode(
    val extensionMode: Int,
) {
    QR_SCAN(ExtensionMode.NONE),
    AUTO(ExtensionMode.AUTO),
    FACE_RETOUCH(ExtensionMode.FACE_RETOUCH),
    PORTRAIT(ExtensionMode.BOKEH),
    NIGHT(ExtensionMode.NIGHT),
    HDR(ExtensionMode.HDR),
    CAMERA(ExtensionMode.NONE),
    VIDEO(ExtensionMode.NONE),
}
