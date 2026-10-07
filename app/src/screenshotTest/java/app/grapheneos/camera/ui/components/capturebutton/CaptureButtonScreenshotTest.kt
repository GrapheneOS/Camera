package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonSize
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import com.android.tools.screenshot.PreviewTest

private val HOLD_TARGET = CaptureButtonTarget(
    direction = CaptureButtonDirection.Start,
    distance = 120.dp,
    icon = PREVIEW_LOCK_ICON,
    accessibilityLabel = "Lock",
)

@PreviewTest
@PreviewLightDark
@Composable
private fun CaptureButtonCores() {
    CaptureButtonFlow {
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Dot,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Square,
            tone = ShutterTone.Recording,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Dot,
            tone = ShutterTone.Recording,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.None,
            icon = PREVIEW_CLOSE_ICON,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            icon = PREVIEW_CLOSE_ICON,
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun CaptureButtonSizes() {
    CaptureButtonFlow {
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            enabled = false,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            size = CaptureButtonSize.Small,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            size = CaptureButtonSize.Small,
            enabled = false,
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun CaptureButtonProgress() {
    CaptureButtonFlow {
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            progress = RingProgress.Indeterminate,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.Disc,
            progress = RingProgress.Determinate(fraction = { 0.6f }),
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.None,
            progress = RingProgress.Segmented(
                segments = 10,
                fraction = { 0.7f },
            ),
            icon = PREVIEW_CLOSE_ICON,
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.None,
            progress = RingProgress.Segmented(
                segments = 3,
                fraction = { 2f / 3 },
            ),
            icon = PREVIEW_CLOSE_ICON,
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun CaptureButtonHold() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            HeldCaptureButton(progress = 0f)
            HeldCaptureButton(progress = 0.5f)
            HeldCaptureButton(progress = 1f)
        }
    }
}

@Composable
private fun CaptureButtonFlow(
    content: @Composable () -> Unit,
) {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun HeldCaptureButton(progress: Float) {
    val travelled = with(LocalDensity.current) { HOLD_TARGET.distance.toPx() } * progress
    val holdState = remember {
        CaptureButtonHoldState().apply {
            start()
            move(
                offset = Offset(x = -travelled, y = 0f),
                armedTarget = HOLD_TARGET.takeIf { progress >= 1f },
            )
        }
    }

    CaptureButton(
        onClick = {},
        core = ShutterCore.Dot,
        modifier = Modifier.padding(start = HOLD_TARGET.distance),
        tone = ShutterTone.Recording,
        onHoldStart = {},
        holdTargets = listOf(HOLD_TARGET),
        holdState = holdState,
    )
}
