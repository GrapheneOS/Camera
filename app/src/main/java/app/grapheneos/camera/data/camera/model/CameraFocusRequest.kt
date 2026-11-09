package app.grapheneos.camera.data.camera.model

import kotlin.time.Duration

data class CameraFocusRequest(
    val id: Long,
    val x: Float,
    val y: Float,
    val autoCancelAfter: Duration,
    val isLocked: Boolean = false,
)
