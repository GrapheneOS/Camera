package app.grapheneos.camera.ui.mediaviewer.components.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.common.components.bottomBar
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.theme.TABULAR_FIGURES
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.util.formatVideoDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val INACTIVE_TRACK_ALPHA = 0.4f

private val CONTROLS_HORIZONTAL_PADDING = 8.dp
private val CONTROLS_TOP_PADDING = 8.dp
private val CONTROLS_BOTTOM_PADDING = 20.dp
private val SEEK_BAR_SPACING = 20.dp
private val SEEK_BAR_INSET = 10.dp
private val SEEK_BAR_TRACK_HEIGHT = 8.dp

@Composable
internal fun VideoControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    position: Duration,
    duration: Duration,
    onPlayPauseClick: () -> Unit,
    onMuteClick: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
    showsButtons: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = MaterialTheme.cameraColors
    val scrim = remember(colors.overlayScrim) {
        Brush.verticalGradient(listOf(Color.Transparent, colors.overlayScrim))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .windowInsetsPadding(WindowInsets.bottomBar)
            .padding(
                start = CONTROLS_HORIZONTAL_PADDING,
                top = CONTROLS_TOP_PADDING,
                end = CONTROLS_HORIZONTAL_PADDING,
                bottom = CONTROLS_BOTTOM_PADDING,
            ),
    ) {
        PlaybackRow(
            showsButtons = showsButtons,
            isPlaying = isPlaying,
            isMuted = isMuted,
            position = position,
            duration = duration,
            onPlayPauseClick = onPlayPauseClick,
            onMuteClick = onMuteClick,
        )
        Spacer(modifier = Modifier.height(SEEK_BAR_SPACING))
        SeekBar(
            position = position,
            duration = duration,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
        )
    }
}

@Composable
private fun PlaybackRow(
    showsButtons: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    position: Duration,
    duration: Duration,
    onPlayPauseClick: () -> Unit,
    onMuteClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current),
    ) {
        AnimatedVisibility(
            visible = showsButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPauseClick,
            )
        }
        PlaybackTime(
            positionSeconds = position.inWholeSeconds,
            durationSeconds = duration.inWholeSeconds,
            modifier = Modifier.align(Alignment.Center),
        )
        AnimatedVisibility(
            visible = showsButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            MuteButton(
                isMuted = isMuted,
                onClick = onMuteClick,
            )
        }
    }
}

@Composable
private fun MuteButton(
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = when {
                isMuted -> Icons.AutoMirrored.Filled.VolumeOff
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = stringResource(
                when {
                    isMuted -> R.string.unmute_audio
                    else -> R.string.mute_audio
                },
            ),
            tint = MaterialTheme.cameraColors.overlay,
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = when {
                isPlaying -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            },
            contentDescription = stringResource(
                when {
                    isPlaying -> R.string.pause_video
                    else -> R.string.play_video
                },
            ),
            tint = MaterialTheme.cameraColors.overlay,
        )
    }
}

@Composable
private fun PlaybackTime(
    positionSeconds: Long,
    durationSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val labelStyle = MaterialTheme.typography.labelLarge
    val textStyle = remember(labelStyle) {
        labelStyle.copy(fontFeatureSettings = TABULAR_FIGURES)
    }

    Text(
        text = stringResource(
            R.string.video_position,
            formatVideoDuration(positionSeconds),
            formatVideoDuration(durationSeconds),
        ),
        color = MaterialTheme.cameraColors.overlay,
        style = textStyle,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    position: Duration,
    duration: Duration,
    onSeek: (Duration) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val contentColor = MaterialTheme.cameraColors.overlay
    val defaultColors = SliderDefaults.colors()
    val colors = remember(defaultColors, contentColor) {
        defaultColors.copy(
            thumbColor = contentColor,
            activeTrackColor = contentColor,
            inactiveTrackColor = contentColor.copy(alpha = INACTIVE_TRACK_ALPHA),
        )
    }
    val durationRange = remember(duration) {
        0f..duration.inWholeMilliseconds.coerceAtLeast(1L).toFloat()
    }

    Slider(
        value = position.inWholeMilliseconds.toFloat(),
        onValueChange = { value -> onSeek(value.toLong().milliseconds) },
        onValueChangeFinished = onSeekFinished,
        valueRange = durationRange,
        colors = colors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                modifier = Modifier.height(SEEK_BAR_TRACK_HEIGHT),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SEEK_BAR_INSET),
    )
}

@PreviewLightDark
@Composable
private fun VideoControlsPreview() {
    CameraPreviewTheme {
        VideoControls(
            isPlaying = true,
            isMuted = false,
            position = 5.seconds,
            duration = 12.seconds,
            onPlayPauseClick = {},
            onMuteClick = {},
            onSeek = {},
            onSeekFinished = {},
        )
    }
}
