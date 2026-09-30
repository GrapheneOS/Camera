package app.grapheneos.camera.ui.components.capturebutton.model

internal sealed interface CaptureButtonHoldEnd {

    data object Released : CaptureButtonHoldEnd

    data object Cancelled : CaptureButtonHoldEnd
}
