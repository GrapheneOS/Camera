package app.grapheneos.camera.ui.components.capturebutton

internal enum class CaptureButtonCore(
    internal val sizeFraction: Float,
    internal val cornerFraction: Float,
) {
    None(
        sizeFraction = 0f,
        cornerFraction = 0.5f,
    ),
    Dot(
        sizeFraction = 0.65f,
        cornerFraction = 0.5f,
    ),
    Disc(
        sizeFraction = 0.85f,
        cornerFraction = 0.5f,
    ),
    Square(
        sizeFraction = 0.5f,
        cornerFraction = 0.15f,
    ),
}
