package app.grapheneos.camera.data.core.model

enum class VideoFrameRate(
    val framesPerSecond: Int,
) {
    FPS_30(framesPerSecond = 30),
    FPS_60(framesPerSecond = 60),
}
