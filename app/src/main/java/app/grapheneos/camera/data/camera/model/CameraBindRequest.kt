package app.grapheneos.camera.data.camera.model

import androidx.camera.video.Quality

data class CameraBindRequest(
    val includesVideoCapture: Boolean,
    val includesImageCapture: Boolean,
    val aspectRatio: Int,
    val imageCaptureTargetRotation: Int,
    val previewTargetRotation: Int,
    val flashMode: Int,
    val photoQuality: Int,
    val waitForFocusLock: Boolean,
    val enableZsl: Boolean,
    val selectHighestResolution: Boolean,
    val videoQuality: Quality,
    val mirrorVideoOnFrontCamera: Boolean,
    val featureGroup: FeatureGroupRequest,
)
