package app.grapheneos.camera.ui.videoplayer.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect as Effect

@Composable
internal fun rememberVideoPlayerEffectHandler(
    onNavigateBack: () -> Unit,
): VideoPlayerEffectHandler {
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val activity = requireNotNull(LocalActivity.current) {
        "The video player needs a hosting activity"
    }

    return remember(activity) {
        VideoPlayerEffectHandlerImpl(
            activity = activity,
            onNavigateBack = { currentOnNavigateBack() },
        )
    }
}

internal interface VideoPlayerEffectHandler {
    fun handle(effect: Effect)
}

internal class VideoPlayerEffectHandlerImpl(
    private val activity: Activity,
    private val onNavigateBack: () -> Unit,
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
        onNavigateBack()
    }
}
