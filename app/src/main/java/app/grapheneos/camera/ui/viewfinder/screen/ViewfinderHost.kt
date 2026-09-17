package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.camera.model.PreviewTarget

class ViewfinderHost(
    val previewTarget: PreviewTarget,
    val chrome: ViewfinderChrome,
    val previewFrames: PreviewFrameHolder,
)
