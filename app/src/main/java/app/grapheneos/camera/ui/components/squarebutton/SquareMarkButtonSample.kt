package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonSize
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.components.lensswitchbutton.LensSwitchButton
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.components.overlayiconbutton.OVERLAY_ICON_BUTTON_SIZE
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconToggleButton
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark
import app.grapheneos.camera.ui.components.thumbnailbutton.ThumbnailButton
import app.grapheneos.camera.ui.components.timerchip.TimerChip
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LENS_SWITCH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_MIC_ICON
import app.grapheneos.camera.ui.core.PREVIEW_MIC_OFF_ICON
import app.grapheneos.camera.ui.core.PREVIEW_PAUSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.PREVIEW_WARM_ICON
import app.grapheneos.camera.ui.core.PreviewRotation
import app.grapheneos.camera.ui.core.previewShot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val TICK = 1.seconds
private val MUTE_TOGGLE_GAP = 16.dp
private val SAVING_DURATION = 1.seconds

private val SAMPLE_PAUSE = SquareButtonMark.Icon(
    icon = PREVIEW_PAUSE_ICON,
)
private val SAMPLE_RESUME = SquareButtonMark.Core(
    core = ShutterCore.Dot,
    tone = ShutterTone.Recording,
)

@Preview(heightDp = 720)
@Composable
private fun SquareMarkButtonSamplePreview() {
    SquareMarkButtonSample()
}

@Composable
private fun SquareMarkButtonSample() {
    val state = remember { SquareMarkButtonSampleState() }

    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            delay(TICK)
            state.tick()
        }
    }
    LaunchedEffect(state.savingRun) {
        if (state.isSaving) {
            delay(SAVING_DURATION)
            state.finishSaving()
        }
    }

    CameraPreviewSample(
        status = state.status,
        controls = {
            CameraPreviewControl(
                text = state.audioLabel,
                onClick = state::switchAudio,
            )
            state.rotation.Control()
        },
    ) {
        ProvideContentRotation(degrees = state.rotation.degrees) {
            AnimatedVisibility(
                visible = state.isRecording,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(all = 16.dp),
                enter = POP_IN,
                exit = POP_OUT,
            ) {
                TimerChip(
                    elapsed = state.elapsed,
                    label = "PAUSED".takeIf { state.isPaused },
                )
            }
            SampleSlots(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(all = 16.dp),
            )
        }
    }
}

@Composable
private fun SampleMuteToggle(
    state: SquareMarkButtonSampleState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isRecording,
        modifier = modifier,
        enter = POP_IN,
        exit = POP_OUT,
    ) {
        OverlayIconToggleButton(
            checked = !state.isAudioOn,
            onCheckedChange = state::mute,
            icon = PREVIEW_MIC_ICON,
            modifier = Modifier.semantics { contentDescription = "Mute audio" },
            checkedIcon = PREVIEW_MIC_OFF_ICON,
        )
    }
}

@Composable
private fun SampleSlots(
    state: SquareMarkButtonSampleState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SampleThumbnailSlot(state = state)
        CaptureButton(
            onClick = state::toggleRecording,
            core = state.core,
            tone = state.tone,
            trigger = CaptureButtonTrigger.Press,
            icon = state.captureIcon,
        )
        SampleSlot(
            isRecording = state.isRecording,
            idle = {
                LensSwitchButton(
                    flipped = state.isFront,
                    onClick = state::flip,
                    icon = PREVIEW_LENS_SWITCH_ICON,
                )
            },
            recording = {
                CaptureButton(
                    onClick = state::takeSnapshot,
                    core = ShutterCore.Disc,
                    size = CaptureButtonSize.Small,
                )
            },
        )
    }
}

@Composable
private fun SampleThumbnailSlot(state: SquareMarkButtonSampleState) {
    Box {
        SampleSlot(
            isRecording = state.isRecording,
            idle = {
                ThumbnailButton(
                    image = state.lastShot,
                    onClick = {},
                    progress = state.thumbnailProgress,
                )
            },
            recording = {
                SquareMarkButton(
                    onClick = state::togglePause,
                    mark = state.pauseMark,
                )
            },
        )
        SampleMuteToggle(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -(OVERLAY_ICON_BUTTON_SIZE + MUTE_TOGGLE_GAP)),
        )
    }
}

@Composable
private fun SampleSlot(
    isRecording: Boolean,
    idle: @Composable () -> Unit,
    recording: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = isRecording,
        modifier = Modifier.size(size = SQUARE_BUTTON_SIZE),
        transitionSpec = { POP_IN togetherWith POP_OUT using null },
        contentAlignment = Alignment.Center,
    ) { shownRecording ->
        when {
            shownRecording -> recording()
            else -> idle()
        }
    }
}

@Stable
private class SquareMarkButtonSampleState {

    val rotation = PreviewRotation()

    var isRecording by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var elapsed by mutableStateOf(Duration.ZERO)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var savingRun by mutableIntStateOf(0)
        private set
    var isFront by mutableStateOf(false)
        private set
    var isAudioOn by mutableStateOf(true)
        private set

    private var snapshots by mutableIntStateOf(0)
    private var shotIndex by mutableIntStateOf(0)
    private val shots = listOf(
        previewShot(top = PREVIEW_WARM_ICON, bottom = PREVIEW_SCENE),
        previewShot(top = PREVIEW_COOL_ICON, bottom = PREVIEW_SCENE),
    )

    val lastShot: ImageBitmap
        get() {
            return shots[shotIndex % shots.size]
        }

    val isRunning: Boolean
        get() {
            return isRecording && !isPaused
        }

    val status: String
        get() {
            val recording = when {
                isRecording && isPaused -> "Paused"
                isRecording -> "Recording"
                isSaving -> "Saving"
                else -> "Stopped"
            }

            return "$recording, snapshots: $snapshots"
        }

    val core: ShutterCore
        get() {
            return when {
                isRecording -> ShutterCore.Square
                else -> ShutterCore.Dot
            }
        }

    val tone: ShutterTone
        get() {
            return when {
                isRecording -> ShutterTone.Recording
                else -> ShutterTone.Neutral
            }
        }

    val thumbnailProgress: RingProgress
        get() {
            return when {
                isSaving -> RingProgress.Indeterminate
                else -> RingProgress.None
            }
        }

    val captureIcon: ImageVector?
        get() {
            return when {
                isAudioOn || isRecording -> null
                else -> PREVIEW_MIC_OFF_ICON
            }
        }

    val audioLabel: String
        get() {
            return when {
                isAudioOn -> "Audio on"
                else -> "Audio off"
            }
        }

    val pauseMark: SquareButtonMark
        get() {
            return when {
                isPaused -> SAMPLE_RESUME
                else -> SAMPLE_PAUSE
            }
        }

    fun toggleRecording() {
        when {
            isRecording -> {
                isSaving = true
                savingRun += 1
                isAudioOn = true
            }
            else -> {
                elapsed = Duration.ZERO
                snapshots = 0
            }
        }
        isRecording = !isRecording
        isPaused = false
    }

    fun switchAudio() {
        isAudioOn = !isAudioOn
    }

    fun mute(muted: Boolean) {
        isAudioOn = !muted
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    fun takeSnapshot() {
        snapshots += 1
    }

    fun finishSaving() {
        isSaving = false
        shotIndex += 1
    }

    fun flip() {
        isFront = !isFront
    }

    fun tick() {
        elapsed += TICK
    }
}
