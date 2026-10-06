package app.grapheneos.camera.ui.components.timerchip

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.crossfadeResizing
import app.grapheneos.camera.ui.components.valuechip.ValueChip
import app.grapheneos.camera.ui.components.valuechip.ValueChipColors
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val LABEL_GAP = 6.dp

/**
 * @param label shown before the time.
 */
@Composable
internal fun TimerChip(
    elapsed: Duration,
    modifier: Modifier = Modifier,
    label: String? = null,
    colors: ValueChipColors = ValueChipColors.fromRecordingTheme(),
) {
    ValueChip(
        modifier = modifier,
        colors = colors,
    ) { textStyle ->
        AnimatedContent(
            targetState = label,
            transitionSpec = { crossfadeResizing() },
            contentAlignment = Alignment.CenterStart,
        ) { shownLabel ->
            if (shownLabel != null) {
                Text(
                    text = shownLabel,
                    modifier = Modifier.padding(end = LABEL_GAP),
                    style = textStyle,
                    maxLines = 1,
                )
            }
        }
        AnimatedContent(
            targetState = rememberTimerChipFormat().format(elapsed = elapsed),
            transitionSpec = { crossfadeResizing() },
            contentAlignment = Alignment.Center,
            contentKey = { it.length },
        ) { time ->
            Text(
                text = time,
                style = textStyle,
                maxLines = 1,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TimerChipPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            TimerChip(elapsed = 1.seconds)
            TimerChip(
                elapsed = 1.seconds,
                label = "PAUSED",
            )
            TimerChip(elapsed = 1.hours + 2.minutes + 3.seconds)
        }
    }
}
