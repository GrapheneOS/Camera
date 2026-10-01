package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
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
    require(trigger == CaptureButtonTrigger.Release || onHoldStart == null) {
        "A press trigger acts before a hold could start"
    }

    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)
    val currentLayoutDirection = rememberUpdatedState(LocalLayoutDirection.current)
    val currentEnabled = rememberUpdatedState(enabled)
    val currentTrigger = rememberUpdatedState(trigger)
    val currentHoldState = rememberUpdatedState(holdState)
    val currentHoldTargets = rememberUpdatedState(holdTargets)
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnHoldStart = rememberUpdatedState(onHoldStart)
    val currentOnHoldDrag = rememberUpdatedState(onHoldDrag)
    val currentOnHoldEnd = rememberUpdatedState(onHoldEnd)

    return remember {
        CaptureButtonGestureListenerImpl(
            currentHapticFeedback = currentHapticFeedback,
            currentLayoutDirection = currentLayoutDirection,
            currentEnabled = currentEnabled,
            currentTrigger = currentTrigger,
            currentHoldState = currentHoldState,
            currentHoldTargets = currentHoldTargets,
            currentOnClick = currentOnClick,
            currentOnHoldStart = currentOnHoldStart,
            currentOnHoldDrag = currentOnHoldDrag,
            currentOnHoldEnd = currentOnHoldEnd,
        )
    }
}

private class CaptureButtonGestureListenerImpl(
    private val currentHapticFeedback: State<HapticFeedback>,
    private val currentLayoutDirection: State<LayoutDirection>,
    private val currentEnabled: State<Boolean>,
    private val currentTrigger: State<CaptureButtonTrigger>,
    private val currentHoldState: State<CaptureButtonHoldState>,
    private val currentHoldTargets: State<List<CaptureButtonTarget>>,
    private val currentOnClick: State<() -> Unit>,
    private val currentOnHoldStart: State<(() -> Unit)?>,
    private val currentOnHoldDrag: State<(Offset) -> Unit>,
    private val currentOnHoldEnd: State<(CaptureButtonHoldEnd) -> Unit>,
) : CaptureButtonGestureListener {

    override val isEnabled: Boolean
        get() {
            return currentEnabled.value
        }

    override val trigger: CaptureButtonTrigger
        get() {
            return currentTrigger.value
        }

    override val isHoldEnabled: Boolean
        get() {
            return currentOnHoldStart.value != null
        }

    override val holdTargets: List<CaptureButtonTarget>
        get() {
            return currentHoldTargets.value
        }

    override val layoutDirection: LayoutDirection
        get() {
            return currentLayoutDirection.value
        }

    override fun onClick() {
        currentOnClick.value()
    }

    override fun onHoldStart() {
        currentHapticFeedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
        currentHoldState.value.start()
        currentOnHoldStart.value?.invoke()
    }

    override fun onHoldMove(
        delta: Offset,
        offset: Offset,
        armedTarget: CaptureButtonTarget?,
    ) {
        val holdState = currentHoldState.value

        if (armedTarget != holdState.armedTarget) {
            currentHapticFeedback.value.performHapticFeedback(armingFeedback(armedTarget))
        }
        holdState.move(
            offset = offset,
            armedTarget = armedTarget,
        )
        if (delta != Offset.Zero) {
            currentOnHoldDrag.value(delta)
        }
    }

    override fun onHoldEnd(end: CaptureButtonHoldEnd) {
        if (end is CaptureButtonHoldEnd.Committed) {
            currentHapticFeedback.value.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        currentHoldState.value.end()
        currentOnHoldEnd.value(end)
    }

    private fun armingFeedback(armedTarget: CaptureButtonTarget?): HapticFeedbackType {
        return when (armedTarget) {
            null -> HapticFeedbackType.SegmentTick
            else -> HapticFeedbackType.GestureThresholdActivate
        }
    }
}
