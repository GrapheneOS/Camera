package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality

data class CameraBindRequest(
    val includesVideoCapture: Boolean,
    val includesImageCapture: Boolean,
    val aspectRatio: AspectRatio,
    val imageCaptureTargetRotation: Int,
    val previewTargetRotation: Int,
    val flashMode: FlashMode,
    val photoQuality: Int,
    val waitForFocusLock: Boolean,
    val enableZsl: Boolean,
    val selectHighestResolution: Boolean,
    val videoQuality: VideoQuality,
    val mirrorVideoOnFrontCamera: Boolean,
    val featureGroup: FeatureGroupRequest,
)
