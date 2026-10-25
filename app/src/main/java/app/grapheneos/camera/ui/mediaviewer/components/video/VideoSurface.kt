package app.grapheneos.camera.ui.mediaviewer.components.video

import android.view.Surface
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.grapheneos.camera.data.media.model.VideoOutput
import java.util.concurrent.atomic.AtomicInteger

// A SurfaceView keeps HDR but cannot be rotated; a TextureView rotates but is tone-mapped to SDR.
// The TextureView is created when a rotation may follow, so that rotating only moves the decoder
// output, and the old surface stays visible until renderedOutputId reports a frame on the new one.
@Composable
internal fun VideoSurface(
    aspectRatio: Float?,
    renderedOutputId: Int?,
    onOutputChange: (VideoOutput?) -> Unit,
    modifier: Modifier = Modifier,
    mayRotate: Boolean = false,
    isRotated: Boolean = false,
) {
    val currentOnOutputChange by rememberUpdatedState(onOutputChange)
    val outputs = remember { VideoOutputs(onActiveChange = { currentOnOutputChange(it) }) }
    val showsTexture = renderedOutputId != null && renderedOutputId == outputs.texture?.id

    SideEffect {
        outputs.prefersTexture = isRotated
    }

    Box(
        modifier = modifier.then(
            when (aspectRatio) {
                null -> Modifier.fillMaxSize()
                else -> Modifier.aspectRatio(aspectRatio)
            },
        ),
    ) {
        AndroidExternalSurface(
            modifier = Modifier
                .fillMaxSize()
                .shownWhen(!showsTexture),
        ) {
            onSurface { surface, _, _ ->
                outputs.updateDirect(surface)
                surface.onDestroyed { outputs.updateDirect(null) }
            }
        }
        if (mayRotate || isRotated || showsTexture) {
            AndroidEmbeddedExternalSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .shownWhen(showsTexture),
            ) {
                onSurface { surface, _, _ ->
                    outputs.updateTexture(surface)
                    surface.onDestroyed { outputs.updateTexture(null) }
                }
            }
        }
    }
}

private fun Modifier.shownWhen(isShown: Boolean): Modifier {
    return graphicsLayer {
        alpha = when {
            isShown -> 1f
            else -> 0f
        }
    }
}

@Stable
private class VideoOutputs(
    private val onActiveChange: (VideoOutput?) -> Unit,
) {

    var texture: VideoOutput? by mutableStateOf(null)
        private set

    var prefersTexture = false
        set(value) {
            field = value
            updateActive()
        }

    private var direct: VideoOutput? = null
    private var active: VideoOutput? = null

    fun updateDirect(surface: Surface?) {
        direct = surface?.let(::outputOf)
        updateActive()
    }

    fun updateTexture(surface: Surface?) {
        texture = surface?.let(::outputOf)
        updateActive()
    }

    private fun updateActive() {
        val next = when {
            prefersTexture && texture != null -> texture
            else -> direct
        }

        if (next != active) {
            active = next
            onActiveChange(next)
        }
    }

    private fun outputOf(surface: Surface): VideoOutput {
        return VideoOutput(
            id = nextOutputId.incrementAndGet(),
            surface = surface,
        )
    }

    private companion object {
        val nextOutputId = AtomicInteger()
    }
}
