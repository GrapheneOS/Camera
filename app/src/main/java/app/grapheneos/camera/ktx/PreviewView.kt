package app.grapheneos.camera.ktx

import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams

fun PreviewView.applyPreviewRatio(aspectRatio: Int, cameraInfo: CameraInfo) {
    val rotation = cameraInfo.getSensorRotationDegrees(display?.rotation ?: Surface.ROTATION_0)
    val (short, long) = if (aspectRatio == AspectRatio.RATIO_16_9) 9 to 16 else 3 to 4

    updateLayoutParams<ConstraintLayout.LayoutParams> {
        dimensionRatio = when {
            rotation % 180 == 0 -> "$long:$short"
            else -> "$short:$long"
        }
    }
}
