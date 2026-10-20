package app.grapheneos.camera.ui.videoplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import app.grapheneos.camera.ui.core.activity.SecureActivity
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import app.grapheneos.camera.ui.videoplayer.screen.videoPlayerArguments
import javax.inject.Inject

internal class VideoPlayerLauncherImpl @Inject constructor() : VideoPlayerLauncher {

    override fun open(
        activity: Activity,
        uri: Uri,
    ) {
        val player = when (activity) {
            is SecureActivity -> SecureVideoPlayerActivity::class.java
            else -> VideoPlayerActivity::class.java
        }
        val intent = Intent(activity, player)
            .putExtras(videoPlayerArguments(uri))

        activity.startActivity(intent)
    }
}
