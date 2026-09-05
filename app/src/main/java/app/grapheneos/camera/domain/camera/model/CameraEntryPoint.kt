package app.grapheneos.camera.domain.camera.model

data class CameraEntryPoint(
    val isSecureSession: Boolean,
    val isCaptureSession: Boolean,
    val isVideoOnlySession: Boolean,
    val requiresVideoModeOnly: Boolean,
    val allowsQrScanning: Boolean,
    val showsCameraModeTabs: Boolean,
)
