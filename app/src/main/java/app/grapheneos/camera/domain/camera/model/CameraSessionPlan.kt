package app.grapheneos.camera.domain.camera.model

import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture

class CameraSessionPlan(
    val videoCapture: VideoCapture<Recorder>?,
    val imageCapture: ImageCapture?,
    val preview: Preview,
    val captureMode: ImageCaptureMode,
    val preferredFeatures: List<GroupableFeature>,
)
