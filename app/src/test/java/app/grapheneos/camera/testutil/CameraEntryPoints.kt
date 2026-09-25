package app.grapheneos.camera.testutil

import app.grapheneos.camera.domain.core.model.CameraEntryPoint

internal fun cameraEntryPoint(
    isSecureSession: Boolean = false,
    isCaptureSession: Boolean = false,
    isVideoOnlySession: Boolean = false,
    requiresVideoModeOnly: Boolean = false,
    allowsQrScanning: Boolean = true,
    showsCameraModeTabs: Boolean = true,
): CameraEntryPoint {
    return CameraEntryPoint(
        isSecureSession = isSecureSession,
        isCaptureSession = isCaptureSession,
        isVideoOnlySession = isVideoOnlySession,
        requiresVideoModeOnly = requiresVideoModeOnly,
        allowsQrScanning = allowsQrScanning,
        showsCameraModeTabs = showsCameraModeTabs,
    )
}
