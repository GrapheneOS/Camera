package app.grapheneos.camera.ui.mediaviewer.navigation

import android.app.Activity
import android.net.Uri

internal interface VideoPlayerLauncher {
    fun open(activity: Activity, uri: Uri)
}
