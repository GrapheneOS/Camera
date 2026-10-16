package app.grapheneos.camera.ui.mediaviewer

import android.app.Activity
import android.net.Uri

interface VideoPlayerLauncher {
    fun open(activity: Activity, uri: Uri)
}
