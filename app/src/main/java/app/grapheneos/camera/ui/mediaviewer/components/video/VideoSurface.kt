package app.grapheneos.camera.ui.mediaviewer.components.video

import android.view.Surface
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

@Composable
internal fun VideoSurface(
    aspectRatio: Float?,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSurfaceCreated by rememberUpdatedState(onSurfaceCreated)
    val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)

    AndroidExternalSurface(
        modifier = modifier.then(
            when (aspectRatio) {
                null -> Modifier.fillMaxSize()
                else -> Modifier.aspectRatio(aspectRatio)
            },
        ),
    ) {
        onSurface { surface, _, _ ->
            currentOnSurfaceCreated(surface)
            surface.onDestroyed {
                currentOnSurfaceDestroyed()
            }
        }
    }
}
