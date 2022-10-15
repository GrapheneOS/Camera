package app.grapheneos.camera.ktx

import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams

fun PreviewView.markAs4by3Layout() = applyRatio(3.0, 4.0)

fun PreviewView.markAs16by9Layout() = applyRatio(9.0, 16.0)

private fun PreviewView.applyRatio(width: Double, height: Double) {
    updateLayoutParams<ConstraintLayout.LayoutParams> {
        dimensionRatio = "H,$width:$height"
    }
}
