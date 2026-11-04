package app.grapheneos.camera.data.orientation.model

import app.grapheneos.camera.data.core.model.DeviceOrientation

data class DeviceMotion(
    val orientation: DeviceOrientation,
    val tiltDegrees: Float,
    val horizonDegrees: Float,
)
