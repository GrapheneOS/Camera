package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize

interface ViewfinderChrome {

    fun thumbnailSize(): ThumbnailSize

    /** The file another app asked this session to write its capture into, if it named one. */
    fun foreignOutputUri(): Uri?

    fun forceUpdateOrientationSensor()
}

internal class ViewfinderChromeImpl(
    private val activity: MainActivity,
) : ViewfinderChrome {

    override fun thumbnailSize(): ThumbnailSize {
        return ThumbnailSize(
            width = activity.imagePreview.width,
            height = activity.imagePreview.height,
        )
    }

    override fun foreignOutputUri(): Uri? {
        return (activity as? CaptureActivity)
            ?.takeIf { it.isOutputUriAvailable() }
            ?.outputUri
    }

    override fun forceUpdateOrientationSensor() {
        activity.forceUpdateOrientationSensor()
    }
}
