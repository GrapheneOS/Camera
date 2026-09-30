package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger

internal interface CaptureButtonGestureListener {

    val isEnabled: Boolean
    val trigger: CaptureButtonTrigger
    val isHoldEnabled: Boolean
    val holdTargets: List<CaptureButtonTarget>
    val layoutDirection: LayoutDirection

    fun onClick()

    fun onHoldStart()

    fun onHoldMove(
        delta: Offset,
        offset: Offset,
        armedTarget: CaptureButtonTarget?,
    )

    fun onHoldEnd(end: CaptureButtonHoldEnd)
}

@Composable
internal fun rememberCaptureButtonGestureListener(
    enabled: Boolean,
    trigger: CaptureButtonTrigger,
    holdState: CaptureButtonHoldState,
    holdTargets: List<CaptureButtonTarget>,
    onClick: () -> Unit,
    onHoldStart: (() -> Unit)?,
    onHoldDrag: (Offset) -> Unit,
    onHoldEnd: (CaptureButtonHoldEnd) -> Unit,
): CaptureButtonGestureListener {
    val hapticFeedback by rememberUpdatedState(LocalHapticFeedback.current)
    val currentLayoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTrigger by rememberUpdatedState(trigger)
    val currentHoldState by rememberUpdatedState(holdState)
    val currentHoldTargets by rememberUpdatedState(holdTargets)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldDrag by rememberUpdatedState(onHoldDrag)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)

    return remember {
        object : CaptureButtonGestureListener {

            override val isEnabled: Boolean
                get() {
                    return currentEnabled
                }

            override val trigger: CaptureButtonTrigger
                get() {
                    return currentTrigger
                }

            override val isHoldEnabled: Boolean
                get() {
                    return currentOnHoldStart != null
                }

            override val holdTargets: List<CaptureButtonTarget>
                get() {
                    return currentHoldTargets
                }

            override val layoutDirection: LayoutDirection
                get() {
                    return currentLayoutDirection
                }

            override fun onClick() {
                currentOnClick()
            }

            override fun onHoldStart() {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentHoldState.start()
                currentOnHoldStart?.invoke()
            }

            override fun onHoldMove(
                delta: Offset,
                offset: Offset,
                armedTarget: CaptureButtonTarget?,
            ) {
                if (armedTarget != currentHoldState.armedTarget) {
                    hapticFeedback.performHapticFeedback(armingFeedback(armedTarget))
                }
                currentHoldState.move(
                    offset = offset,
                    armedTarget = armedTarget,
                )
                if (delta != Offset.Zero) {
                    currentOnHoldDrag(delta)
                }
            }

            override fun onHoldEnd(end: CaptureButtonHoldEnd) {
                if (end is CaptureButtonHoldEnd.Committed) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                currentHoldState.end()
                currentOnHoldEnd(end)
            }
        }
    }
}

private fun armingFeedback(armedTarget: CaptureButtonTarget?): HapticFeedbackType {
    return when (armedTarget) {
        null -> HapticFeedbackType.SegmentTick
        else -> HapticFeedbackType.GestureThresholdActivate
    }
}
