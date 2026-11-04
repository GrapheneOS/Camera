package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.JpegQuality

data class PhotoBindSettings(
    val flashMode: FlashMode,
    val quality: JpegQuality,
    val captureMode: CaptureMode,
    val selectHighestResolution: Boolean,
    val ultraHdr: Boolean,
)
