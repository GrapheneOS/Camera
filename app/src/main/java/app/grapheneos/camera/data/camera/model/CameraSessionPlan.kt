package app.grapheneos.camera.data.camera.model

import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import app.grapheneos.camera.data.core.model.CaptureMode

class CameraSessionPlan(
    val videoCapture: VideoCapture<Recorder>?,
    val imageCapture: ImageCapture?,
    val preview: Preview,
    val captureMode: CaptureMode,
    val preferredFeatures: List<GroupableFeature>,
)
