package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.gesture.captureButtonInput
import app.grapheneos.camera.ui.components.capturebutton.gesture.rememberCaptureButtonGestureListener
import app.grapheneos.camera.ui.components.capturebutton.gesture.rememberCaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.gesture.rememberCaptureButtonKeyHandler
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON

private val BUTTON_SIZE = 84.dp

/**
 * Passing [onHoldStart] lets the button be held. While held, its core can be dragged out to
 * [holdTargets], which are drawn outside the button's bounds.
 */
@Composable
internal fun CaptureButton(
    onClick: () -> Unit,
    core: CaptureButtonCore,
    modifier: Modifier = Modifier,
    tone: CaptureButtonTone = CaptureButtonTone.Neutral,
    enabled: Boolean = true,
    progress: CaptureButtonProgress = CaptureButtonProgress.None,
    trigger: CaptureButtonTrigger = CaptureButtonTrigger.Release,
    icon: ImageVector? = null,
    iconRotationDegrees: Float = 0f,
    onHoldStart: (() -> Unit)? = null,
    onHoldDrag: (delta: Offset) -> Unit = {},
    onHoldEnd: (CaptureButtonHoldEnd) -> Unit = {},
    holdTargets: List<CaptureButtonTarget> = emptyList(),
    holdState: CaptureButtonHoldState = rememberCaptureButtonHoldState(),
    colors: CaptureButtonColors = CaptureButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val alpha by animateEnabledAlpha(enabled = enabled)
    val keyHandler = rememberCaptureButtonKeyHandler(
        interactionSource = resolvedInteractionSource,
        enabled = enabled,
    )
    val gestureListener = rememberCaptureButtonGestureListener(
        enabled = enabled,
        trigger = trigger,
        holdState = holdState,
        holdTargets = holdTargets,
        onClick = onClick,
        onHoldStart = onHoldStart,
        onHoldDrag = onHoldDrag,
        onHoldEnd = onHoldEnd,
    )

    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .graphicsLayer { this.alpha = alpha }
            .captureButtonInput(
                enabled = enabled,
                holdTargets = holdTargets,
                listener = gestureListener,
                keyHandler = keyHandler,
                interactionSource = resolvedInteractionSource,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CaptureButtonLayers(
            core = core,
            tone = tone,
            progress = progress,
            icon = icon,
            iconRotationDegrees = iconRotationDegrees,
            holdTargets = holdTargets,
            holdState = holdState,
            colors = colors,
            interactionSource = resolvedInteractionSource,
        )
    }
}

@Composable
private fun CaptureButtonLayers(
    core: CaptureButtonCore,
    tone: CaptureButtonTone,
    progress: CaptureButtonProgress,
    icon: ImageVector?,
    iconRotationDegrees: Float,
    holdTargets: List<CaptureButtonTarget>,
    holdState: CaptureButtonHoldState,
    colors: CaptureButtonColors,
    interactionSource: InteractionSource,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val targetVisibility by animateVisibility(isVisible = holdState.isHeld)
    val iconRotation by animateRotation(degrees = iconRotationDegrees)
    val pull = animateHoldPull(
        holdState = holdState,
        targets = holdTargets,
    )

    CaptureButtonTargetBackgrounds(
        targets = holdTargets,
        holdState = holdState,
        visibility = { targetVisibility },
        color = colors.containerColor,
    )
    CaptureButtonFace(
        core = core,
        isPressed = isPressed,
        isHeld = holdState.isHeld,
        isFocused = isFocused,
        hasProgress = progress != CaptureButtonProgress.None,
        coreColor = colors.coreColor(tone),
        containerColor = colors.containerColor,
        focusColor = colors.focusColor,
        pull = pull,
        dockProgress = holdDockProgress(pull = pull, targets = holdTargets),
        modifier = Modifier.fillMaxSize(),
    )
    CaptureButtonProgressIndicator(
        progress = progress,
        color = colors.progressColor,
        trackColor = colors.progressTrackColor,
        modifier = Modifier.fillMaxSize(),
    )
    CaptureButtonIcon(
        icon = icon,
        isPressed = isPressed,
        isHeld = holdState.isHeld,
        tint = colors.contentColor(core),
        pull = pull,
        rotation = { iconRotation },
    )
    CaptureButtonTargetIcons(
        targets = holdTargets,
        holdState = holdState,
        visibility = { targetVisibility },
        rotation = { iconRotation },
        tint = colors.contentColor,
    )
}

@PreviewLightDark
@Composable
private fun CaptureButtonPreview() {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Dot,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Square,
                tone = CaptureButtonTone.Recording,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Dot,
                tone = CaptureButtonTone.Recording,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                enabled = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CaptureButtonProgressPreview() {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                progress = CaptureButtonProgress.Indeterminate,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                progress = CaptureButtonProgress.Determinate(
                    fraction = { 0.6f },
                ),
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 10,
                    fraction = { 0.7f },
                ),
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 3,
                    fraction = { 2f / 3 },
                ),
                icon = PREVIEW_CLOSE_ICON,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CaptureButtonHoldPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewHeldCaptureButton(progress = 0f)
            PreviewHeldCaptureButton(progress = 0.5f)
            PreviewHeldCaptureButton(progress = 1f)
        }
    }
}

@Composable
private fun PreviewHeldCaptureButton(progress: Float) {
    val travelled = with(LocalDensity.current) { PREVIEW_TARGET.distance.toPx() } * progress
    val holdState = remember {
        CaptureButtonHoldState().apply {
            start()
            move(
                offset = Offset(x = -travelled, y = 0f),
                armedTarget = PREVIEW_TARGET.takeIf { progress >= 1f },
            )
        }
    }

    CaptureButton(
        onClick = {},
        core = CaptureButtonCore.Dot,
        modifier = Modifier.padding(start = PREVIEW_TARGET.distance),
        tone = CaptureButtonTone.Recording,
        onHoldStart = {},
        holdTargets = listOf(PREVIEW_TARGET),
        holdState = holdState,
    )
}

@Composable
private fun PreviewCaptureButton(
    core: CaptureButtonCore,
    tone: CaptureButtonTone = CaptureButtonTone.Neutral,
    enabled: Boolean = true,
    progress: CaptureButtonProgress = CaptureButtonProgress.None,
    icon: ImageVector? = null,
) {
    CaptureButton(
        onClick = {},
        core = core,
        tone = tone,
        enabled = enabled,
        progress = progress,
        icon = icon,
    )
}

private val PREVIEW_TARGET = CaptureButtonTarget(
    direction = CaptureButtonDirection.Start,
    distance = 120.dp,
    icon = PREVIEW_LOCK_ICON,
    accessibilityLabel = "Lock",
)
