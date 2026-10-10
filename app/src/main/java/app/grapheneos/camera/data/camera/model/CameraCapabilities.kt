package app.grapheneos.camera.data.camera.model

data class CameraCapabilities(
    val isZslSupported: Boolean = false,
    val isVideoStabilizationSupported: Boolean = false,
    val photoResolutions: PhotoResolutions? = null,
)
