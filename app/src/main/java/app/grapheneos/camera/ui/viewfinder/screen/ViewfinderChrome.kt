package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize

interface ViewfinderChrome {

    fun thumbnailSize(): ThumbnailSize

    fun forceUpdateOrientationSensor()
}
