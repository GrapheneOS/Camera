package app.grapheneos.camera.data.camera.model

import androidx.camera.video.Quality

// Every setting that reaches one of the three probed SessionConfigs has to appear in the
// key. A setting added to the ImageCapture, Recorder or Preview builder without being added
// here would be answered from a verdict that predates it, which either takes in-video
// snapshots away for no reason or keeps them on a camera that cannot bind them.
data class SnapshotProbeKey(
    val lensFacing: Int,
    val videoQuality: Quality,
    val usesFeatureGroup: Boolean,
    val captureMode: ImageCaptureMode,
    val selectHighestResolution: Boolean,
)
