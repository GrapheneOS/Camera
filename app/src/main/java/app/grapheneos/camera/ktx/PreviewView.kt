package app.grapheneos.camera.ktx

import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout

fun PreviewView.markAs4by3Layout() = applyRatio("3:4")

fun PreviewView.markAs16by9Layout() = applyRatio("9:16")

private fun PreviewView.applyRatio(radio: String) {
    val updatedLayoutPrams = layoutParams as ConstraintLayout.LayoutParams
    updatedLayoutPrams.dimensionRatio = "H,$radio"
    layoutParams = updatedLayoutPrams
}