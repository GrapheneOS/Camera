package app.grapheneos.camera.data.core.model

enum class CameraMode(
    val extensionMode: ExtensionMode?,
) {
    QR_SCAN(null),
    AUTO(ExtensionMode.AUTO),
    FACE_RETOUCH(ExtensionMode.FACE_RETOUCH),
    PORTRAIT(ExtensionMode.BOKEH),
    NIGHT(ExtensionMode.NIGHT),
    HDR(ExtensionMode.HDR),
    CAMERA(null),
    VIDEO(null),
    ;

    val isQr: Boolean
        get() {
            return this == QR_SCAN
        }

    val isVideo: Boolean
        get() {
            return this == VIDEO
        }
}
