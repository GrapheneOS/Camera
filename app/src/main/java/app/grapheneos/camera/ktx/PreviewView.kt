package app.grapheneos.camera.ktx

import android.view.Surface
import androidx.camera.core.CameraInfo
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import app.grapheneos.camera.data.core.model.AspectRatio

fun PreviewView.applyPreviewRatio(aspectRatio: AspectRatio, cameraInfo: CameraInfo) {
    val rotation = cameraInfo.getSensorRotationDegrees(display?.rotation ?: Surface.ROTATION_0)
    val (short, long) = if (aspectRatio == AspectRatio.RATIO_16_9) 9 to 16 else 3 to 4

    updateLayoutParams<ConstraintLayout.LayoutParams> {
        dimensionRatio = when {
            rotation % 180 == 0 -> "$long:$short"
            else -> "$short:$long"
        }
    }
}
