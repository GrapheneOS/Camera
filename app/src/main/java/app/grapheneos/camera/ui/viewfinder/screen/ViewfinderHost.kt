package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize

class ViewfinderHost(
    val previewTarget: PreviewTarget,
    val chrome: ViewfinderChrome,
    val previewFrames: PreviewFrameHolder,
    val thumbnailSize: ThumbnailSize,
)
