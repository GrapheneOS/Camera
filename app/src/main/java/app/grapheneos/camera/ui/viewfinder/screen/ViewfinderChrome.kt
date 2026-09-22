package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize

interface ViewfinderChrome {

    fun thumbnailSize(): ThumbnailSize

    /** The file another app asked this session to record into, if it named one. */
    fun foreignOutputUri(): Uri?

    fun forceUpdateOrientationSensor()
}
