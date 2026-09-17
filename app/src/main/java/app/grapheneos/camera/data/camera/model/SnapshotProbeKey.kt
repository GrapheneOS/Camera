package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.VideoQuality

// Every setting that reaches one of the three probed SessionConfigs has to appear in the
// key. A setting added to the ImageCapture, Recorder or Preview builder without being added
// here would be answered from a verdict that predates it, which either takes in-video
// snapshots away for no reason or keeps them on a camera that cannot bind them.
data class SnapshotProbeKey(
    val lensFacing: LensFacing,
    val videoQuality: VideoQuality,
    val usesFeatureGroup: Boolean,
    val captureMode: ImageCaptureMode,
    val selectHighestResolution: Boolean,
)
