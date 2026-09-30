package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonGestureListener
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonKeyHandler
import app.grapheneos.camera.ui.components.capturebutton.gesture.detectCaptureButtonGestures
import app.grapheneos.camera.ui.components.capturebutton.gesture.rememberCaptureButtonGestureListener
import app.grapheneos.camera.ui.components.capturebutton.gesture.rememberCaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import kotlinx.coroutines.flow.collectLatest

private val BUTTON_SIZE = 84.dp
private val ICON_SIZE = 24.dp
private val FOCUS_RING_WIDTH = 3.dp
private val PROGRESS_PADDING = 4.dp
private val PROGRESS_GAP = 4.dp
private val PROGRESS_INSET = PROGRESS_PADDING +
    ProgressIndicatorDefaults.CircularStrokeWidth +
    PROGRESS_GAP

private const val PRESSED_SCALE = 1.1f
private const val HELD_SCALE = 1.15f
private const val PRESSED_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.35f
private const val FULL_TURN = 360f

private val PREVIEW_TARGET = CaptureButtonTarget(
    direction = CaptureButtonDirection.Start,
    distance = 120.dp,
    icon = PREVIEW_LOCK_ICON,
    accessibilityLabel = "Lock",
)

private val LIFT_SPEC = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 500f,
)
private val LIFT_COLOR_SPEC = spring<Color>(
    stiffness = 500f,
)
private val MORPH_SPEC = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = 500f,
)
private val PULL_SPEC = spring(
    dampingRatio = 0.7f,
    stiffness = 500f,
    visibilityThreshold = Offset.VisibilityThreshold,
)

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
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val isFocused by resolvedInteractionSource.collectIsFocusedAsState()

    val alpha by animateEnabledAlpha(enabled = enabled)
    val targetVisibility by animateVisibility(isVisible = holdState.isHeld)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val iconRotation by animateRotation(degrees = iconRotationDegrees)

    val keyHandler = remember(resolvedInteractionSource) {
        CaptureButtonKeyHandler(interactionSource = resolvedInteractionSource)
    }

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

    val animatedPull by animateHoldPull(
        holdState = holdState,
        targets = holdTargets,
    )
    val pull = {
        when {
            holdState.isHeld -> holdPull(
                offset = holdState.offset,
                targets = holdTargets,
                density = density,
                layoutDirection = layoutDirection,
            )

            else -> animatedPull
        }
    }

    require(trigger == CaptureButtonTrigger.Release || onHoldStart == null) {
        "A press trigger acts before a hold could start"
    }

    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .graphicsLayer {
                this.alpha = alpha
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.onClick(
                    label = null,
                    action = {
                        gestureListener.onClick()
                        true
                    },
                )
                if (!enabled) {
                    disabled()
                }
                if (enabled && onHoldStart != null) {
                    customActions = holdAccessibilityActions(
                        targets = holdTargets,
                        listener = gestureListener,
                    )
                }
            }
            .onKeyEvent { event ->
                enabled && keyHandler.onKeyEvent(
                    event = event,
                    trigger = trigger,
                    onClick = onClick,
                )
            }
            .focusable(
                enabled = enabled,
                interactionSource = resolvedInteractionSource,
            )
            .pointerInput(resolvedInteractionSource) {
                detectCaptureButtonGestures(
                    interactionSource = resolvedInteractionSource,
                    listener = gestureListener,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        holdTargets.forEach { target ->
            key(target) {
                CaptureButtonTargetBackground(
                    target = target,
                    holdState = holdState,
                    visibility = { targetVisibility },
                    color = colors.containerColor,
                )
            }
        }
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
            dockProgress = {
                holdDockProgress(
                    pull = pull(),
                    targets = holdTargets,
                    density = density,
                    layoutDirection = layoutDirection,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        CaptureButtonProgressIndicator(
            progress = progress,
            color = colors.progressColor,
            trackColor = colors.progressTrackColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(all = PROGRESS_PADDING),
        )
        CaptureButtonIcon(
            icon = icon,
            isPressed = isPressed,
            isHeld = holdState.isHeld,
            tint = colors.contentColor(core),
            pull = pull,
            rotation = { iconRotation },
        )
        holdTargets.forEach { target ->
            key(target) {
                CaptureButtonTargetIcon(
                    target = target,
                    holdState = holdState,
                    visibility = { targetVisibility },
                    rotation = { iconRotation },
                    tint = colors.contentColor,
                )
            }
        }
    }
}

@Composable
private fun CaptureButtonFace(
    core: CaptureButtonCore,
    isPressed: Boolean,
    isHeld: Boolean,
    isFocused: Boolean,
    hasProgress: Boolean,
    coreColor: Color,
    containerColor: Color,
    focusColor: Color,
    pull: () -> Offset,
    dockProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val sizeFraction by animateFloatAsState(
        targetValue = core.sizeFraction,
        animationSpec = MORPH_SPEC,
    )
    val cornerFraction by animateFloatAsState(
        targetValue = core.cornerFraction,
        animationSpec = MORPH_SPEC,
    )
    val pressedScale by animatePressedScale(
        isPressed = isPressed && !hasProgress,
        isHeld = isHeld && !hasProgress,
    )
    val progressInsetFraction by animateFloatAsState(
        targetValue = when {
            hasProgress -> 1f
            else -> 0f
        },
        animationSpec = MORPH_SPEC,
    )
    val animatedCoreColor by animatePressedColor(
        color = coreColor,
        isPressed = isPressed,
        isHeld = isHeld,
    )

    Canvas(modifier = modifier) {
        val progressInset = PROGRESS_INSET.toPx() * progressInsetFraction
        val coreSize = coreSize(
            sizeFraction = sizeFraction,
            pressedScale = pressedScale,
            maxRestingSize = size.minDimension - progressInset * 2,
            dockProgress = dockProgress(),
        )

        drawCircle(
            color = containerColor,
        )
        translate(
            left = pull().x,
            top = pull().y,
        ) {
            drawCore(
                color = animatedCoreColor,
                coreSize = coreSize,
                cornerFraction = cornerFraction,
            )
        }
        if (isFocused) {
            drawFocusRing(color = focusColor)
        }
    }
}

@Composable
private fun CaptureButtonIcon(
    icon: ImageVector?,
    isPressed: Boolean,
    isHeld: Boolean,
    tint: Color,
    pull: () -> Offset,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val animatedTint by animateColorAsState(targetValue = tint)
    val pressedScale by animatePressedScale(
        isPressed = isPressed,
        isHeld = isHeld,
    )

    Crossfade(
        targetState = icon,
        modifier = modifier.graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
            translationX = pull().x
            translationY = pull().y
            rotationZ = rotation()
        },
    ) { targetIcon ->
        if (targetIcon != null) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = animatedTint,
            )
        }
    }
}

@Composable
private fun animateHoldPull(
    holdState: CaptureButtonHoldState,
    targets: List<CaptureButtonTarget>,
): State<Offset> {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentTargets by rememberUpdatedState(targets)
    val pull = remember {
        Animatable(
            initialValue = Offset.Zero,
            typeConverter = Offset.VectorConverter,
        )
    }

    LaunchedEffect(
        holdState,
        density,
        layoutDirection,
    ) {
        snapshotFlow {
            holdPull(
                offset = holdState.offset,
                targets = currentTargets,
                density = density,
                layoutDirection = layoutDirection,
            )
        }.collectLatest { target ->
            when {
                holdState.isHeld -> pull.snapTo(
                    targetValue = target,
                )

                else -> pull.animateTo(
                    targetValue = target,
                    animationSpec = PULL_SPEC,
                )
            }
        }
    }

    return pull.asState()
}

@Composable
private fun animateVisibility(isVisible: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isVisible -> 1f
            else -> 0f
        },
    )
}

@Composable
private fun animateRotation(degrees: Float): State<Float> {
    val rotation = remember { Animatable(initialValue = degrees) }

    LaunchedEffect(degrees) {
        rotation.animateTo(
            targetValue = rotation.value + shortestTurn(
                from = rotation.value,
                to = degrees,
            ),
            animationSpec = MORPH_SPEC,
        )
    }

    return rotation.asState()
}

private fun shortestTurn(
    from: Float,
    to: Float,
): Float {
    return ((to - from) % FULL_TURN + FULL_TURN * 1.5f) % FULL_TURN - FULL_TURN / 2
}

private fun holdPull(
    offset: Offset,
    targets: List<CaptureButtonTarget>,
    density: Density,
    layoutDirection: LayoutDirection,
): Offset {
    var strongestPull = Offset.Zero

    targets.fastForEach { target ->
        val pull = target.pull(
            offset = offset,
            density = density,
            layoutDirection = layoutDirection,
        )

        if (pull.getDistanceSquared() > strongestPull.getDistanceSquared()) {
            strongestPull = pull
        }
    }

    return strongestPull
}

private fun holdDockProgress(
    pull: Offset,
    targets: List<CaptureButtonTarget>,
    density: Density,
    layoutDirection: LayoutDirection,
): Float {
    val progress = targets.fastMaxOfOrNull { target ->
        target.progress(
            offset = pull,
            density = density,
            layoutDirection = layoutDirection,
        )
    }

    return progress ?: 0f
}

private fun holdAccessibilityActions(
    targets: List<CaptureButtonTarget>,
    listener: CaptureButtonGestureListener,
): List<CustomAccessibilityAction> {
    return targets.map { target ->
        CustomAccessibilityAction(
            label = target.accessibilityLabel,
            action = {
                listener.onHoldStart()
                listener.onHoldEnd(CaptureButtonHoldEnd.Committed(target = target))
                true
            },
        )
    }
}

@Composable
private fun animatePressedScale(
    isPressed: Boolean,
    isHeld: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isHeld -> HELD_SCALE
            isPressed -> PRESSED_SCALE
            else -> 1f
        },
        animationSpec = when {
            isHeld -> LIFT_SPEC
            else -> MORPH_SPEC
        },
    )
}

@Composable
private fun animatePressedColor(
    color: Color,
    isPressed: Boolean,
    isHeld: Boolean,
): State<Color> {
    return animateColorAsState(
        targetValue = when {
            isPressed && !isHeld -> color.copy(alpha = color.alpha * PRESSED_ALPHA)
            else -> color
        },
        animationSpec = when {
            isHeld -> LIFT_COLOR_SPEC
            else -> spring()
        },
    )
}

@Composable
private fun animateEnabledAlpha(
    enabled: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            enabled -> 1f
            else -> DISABLED_ALPHA
        },
    )
}

private fun DrawScope.coreSize(
    sizeFraction: Float,
    pressedScale: Float,
    maxRestingSize: Float,
    dockProgress: Float,
): Float {
    val restingSize = (size.minDimension * sizeFraction).coerceAtMost(maxRestingSize)
    val naturalSize = restingSize * pressedScale
    val dockedSize = dockedCoreSize(
        progress = dockProgress,
        density = this,
    )

    return lerp(
        start = naturalSize,
        stop = naturalSize.coerceAtMost(dockedSize),
        fraction = dockProgress,
    )
}

private fun DrawScope.drawCore(
    color: Color,
    coreSize: Float,
    cornerFraction: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = center - Offset(x = coreSize / 2, y = coreSize / 2),
        size = Size(width = coreSize, height = coreSize),
        cornerRadius = CornerRadius(coreSize * cornerFraction),
    )
}

private fun DrawScope.drawFocusRing(
    color: Color,
) {
    val width = FOCUS_RING_WIDTH.toPx()

    drawCircle(
        color = color,
        radius = (size.minDimension - width) / 2,
        style = Stroke(width = width),
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
                    fraction = 0.6f,
                ),
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 10,
                    filled = 7,
                ),
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 3,
                    filled = 2,
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
