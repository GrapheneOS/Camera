package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.camera.model.LensFacing

data class ViewfinderBindTarget(
    val rotation: Int,
    val qrLensFacing: LensFacing?,
)
