package app.grapheneos.camera.ui.videoplayer.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect as Effect

@Composable
internal fun rememberVideoPlayerEffectHandler(): VideoPlayerEffectHandler {
    val activity = requireNotNull(LocalActivity.current) {
        "The video player needs a hosting activity"
    }

    return remember(activity) {
        VideoPlayerEffectHandlerImpl(activity)
    }
}

internal interface VideoPlayerEffectHandler {
    fun handle(effect: Effect)
}

internal class VideoPlayerEffectHandlerImpl(
    private val activity: Activity,
) : VideoPlayerEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.PlaybackFailed -> closeWithError()
        }
    }

    private fun closeWithError() {
        Toast
            .makeText(
                activity.applicationContext,
                R.string.unable_to_play_video,
                Toast.LENGTH_SHORT,
            )
            .show()
        activity.finish()
    }
}
