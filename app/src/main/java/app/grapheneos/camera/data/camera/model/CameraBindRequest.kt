package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.AspectRatio

data class CameraBindRequest(
    val aspectRatio: AspectRatio,
    val imageCaptureTargetRotation: Int,
    val previewTargetRotation: Int,
    val photo: PhotoBindSettings?,
    val video: VideoBindSettings?,
    val lensFeatures: LensFeatures,
    val featureGroup: FeatureGroupRequest,
)
