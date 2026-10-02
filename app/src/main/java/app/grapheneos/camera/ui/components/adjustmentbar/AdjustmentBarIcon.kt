package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

private const val PRESSED_SCALE = 1.1f

private val REPEAT_INTERVAL = 100.milliseconds

@Composable
internal fun AdjustmentBarIcon(
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    tryStep: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnStep = rememberUpdatedState(tryStep)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> PRESSED_SCALE
            else -> 1f
        },
    )

    Box(
        // Not clickable: TalkBack and keys already step the bar through its slider actions.
        modifier = modifier.pointerInput(Unit) {
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis.milliseconds

            detectTapGestures(
                onPress = {
                    if (currentEnabled.value) {
                        isPressed = true
                        repeatWhileHeld(
                            delay = longPressTimeout,
                            tryStep = { currentEnabled.value && currentOnStep.value() },
                        )
                        isPressed = false
                    }
                },
                onLongPress = {},
                onTap = {
                    if (currentEnabled.value) {
                        currentOnStep.value()
                    }
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            tint = tint,
        )
    }
}

private suspend fun PressGestureScope.repeatWhileHeld(
    delay: Duration,
    tryStep: () -> Boolean,
) {
    var isHeld = withTimeoutOrNull(delay) { tryAwaitRelease() } == null

    while (isHeld && tryStep()) {
        isHeld = withTimeoutOrNull(REPEAT_INTERVAL) { tryAwaitRelease() } == null
    }
    if (isHeld) {
        tryAwaitRelease()
    }
}
