package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.mediaviewer.components.video.VIDEO_SKIP_AREA_FRACTION
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoSkipAreaShape
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection

@Composable
internal fun VideoSkipWaves(
    presses: VideoSkipPresses,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkipDirection.entries.forEach { direction ->
            SkipWaveArea(
                direction = direction,
                presses = presses.sourceOf(direction),
                modifier = Modifier.align(
                    when (direction) {
                        SkipDirection.Back -> Alignment.CenterStart
                        SkipDirection.Forward -> Alignment.CenterEnd
                    },
                ),
            )
        }
    }
}

@Composable
private fun SkipWaveArea(
    direction: SkipDirection,
    presses: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.cameraColors.overlay
    val indication = remember(color) { VideoSkipWaveIndication(color) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(VIDEO_SKIP_AREA_FRACTION)
            .clip(VideoSkipAreaShape(direction))
            .indication(
                interactionSource = presses,
                indication = indication,
            ),
    )
}

@Stable
internal class VideoSkipPresses {

    private val sources = SkipDirection.entries.associateWith { MutableInteractionSource() }

    fun press(
        direction: SkipDirection,
        positionInArea: Offset,
    ) {
        sourceOf(direction).tryEmit(PressInteraction.Press(positionInArea))
    }

    fun sourceOf(direction: SkipDirection): MutableInteractionSource {
        return sources.getValue(direction)
    }
}
