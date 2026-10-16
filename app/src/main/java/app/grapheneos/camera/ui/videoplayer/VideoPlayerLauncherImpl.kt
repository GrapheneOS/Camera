package app.grapheneos.camera.ui.videoplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import app.grapheneos.camera.ui.activities.SecureActivity
import app.grapheneos.camera.ui.common.navigation.VideoPlayerLauncher
import javax.inject.Inject

internal class VideoPlayerLauncherImpl @Inject constructor() : VideoPlayerLauncher {

    override fun open(activity: Activity, uri: Uri) {
        val player = when (activity) {
            is SecureActivity -> SecureVideoPlayerActivity::class.java
            else -> VideoPlayerActivity::class.java
        }
        val intent = Intent(activity, player)
            .putExtra(VideoPlayerActivity.VIDEO_URI, uri)

        activity.startActivity(intent)
    }
}
