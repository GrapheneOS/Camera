package app.grapheneos.camera.ktx

import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import app.grapheneos.camera.data.core.model.AspectRatio

fun PreviewView.applyPreviewRatio(aspectRatio: AspectRatio, sensorOrientationDegrees: Int) {
    val displayRotationDegrees = when (display?.rotation ?: Surface.ROTATION_0) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    // Only whether the preview is sideways matters, and that is the same whether the display
    // rotation is added to the sensor orientation (front lens) or subtracted from it (rear lens).
    val rotation = sensorOrientationDegrees + displayRotationDegrees
    val (short, long) = when (aspectRatio) {
        AspectRatio.RATIO_16_9 -> 9 to 16
        AspectRatio.RATIO_4_3 -> 3 to 4
    }

    updateLayoutParams<ConstraintLayout.LayoutParams> {
        dimensionRatio = when {
            rotation % 180 == 0 -> "$long:$short"
            else -> "$short:$long"
        }
    }
}
