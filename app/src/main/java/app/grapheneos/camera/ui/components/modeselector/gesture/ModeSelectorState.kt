package app.grapheneos.camera.ui.components.modeselector.gesture

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.gesture.DragEndInteractions
import app.grapheneos.camera.ui.components.modeselector.ModeSelectorGeometry
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import kotlinx.coroutines.flow.collectLatest

@Stable
internal class ModeSelectorState(
    initialIndex: Int,
    private val hapticFeedback: State<HapticFeedback>,
) : DraggableState,
    DragScope {

    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    var isDragging by mutableStateOf(false)
        private set

    val isIdle: Boolean
        get() {
            return !isDragging && settleRequest == null
        }

    internal var settleRequest by mutableStateOf<SettleRequest?>(null)
        private set

    internal var revision by mutableIntStateOf(0)
        private set

    internal var geometry by mutableStateOf(
        ModeSelectorGeometry(
            itemWidths = emptyList(),
            containerWidth = 0f,
            highlightInset = 0f,
        ),
    )

    internal var onSettled: (Int) -> Unit = {}

    internal val dragInteractions: MutableInteractionSource = DragEndInteractions(
        onStop = ::release,
        onCancel = ::cancel,
    )

    private var labels: List<String>? = null

    private val mutex = MutatorMutex()

    fun settleNow() {
        val request = settleRequest ?: return

        position = request.index.toFloat()
        finishSettle(request = request)
    }

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit,
    ) {
        mutex.mutateWith(
            receiver = this,
            priority = dragPriority,
        ) {
            isDragging = true
            settleRequest = null
            block()
        }
    }

    override fun dispatchRawDelta(delta: Float) {
        dragBy(pixels = delta)
    }

    override fun dragBy(pixels: Float) {
        moveTo(
            newPosition = geometry.position(center = geometry.center(position = position) - pixels),
            ticks = true,
        )
    }

    internal fun select(index: Int) {
        settleRequest = SettleRequest(index = index)
    }

    internal fun anchor(
        labels: List<String>,
        selectedIndex: Int,
    ) {
        val previousLabels = this.labels

        this.labels = labels
        if (previousLabels != null && previousLabels != labels) {
            settleRequest = null
            position = selectedIndex.toFloat()
            revision++
        }
    }

    internal suspend fun settle(request: SettleRequest) {
        animateTo(index = request.index) { value ->
            if (settleRequest === request) {
                moveTo(
                    newPosition = value,
                    ticks = true,
                )
            }
        }
        finishSettle(request = request)
    }

    internal suspend fun syncTo(index: Int) {
        animateTo(index = index) { value ->
            moveTo(
                newPosition = value,
                ticks = false,
            )
        }
    }

    private fun release() {
        settleRequest = SettleRequest(index = geometry.nearestIndex(position = position))
        isDragging = false
    }

    private fun cancel() {
        isDragging = false
        revision++
    }

    private suspend fun animateTo(
        index: Int,
        onFrame: (Float) -> Unit,
    ) {
        val startRevision = revision

        mutex.mutate {
            animate(
                initialValue = position,
                targetValue = index.toFloat(),
                animationSpec = SETTLE_SPEC,
            ) { value, _ ->
                if (revision == startRevision) {
                    onFrame(value)
                }
            }
        }
    }

    private fun moveTo(
        newPosition: Float,
        ticks: Boolean,
    ) {
        val previousIndex = geometry.nearestIndex(position = position)

        position = newPosition
        if (ticks && geometry.nearestIndex(position = position) != previousIndex) {
            hapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    private fun finishSettle(request: SettleRequest) {
        if (settleRequest === request) {
            settleRequest = null
            revision++
            onSettled(request.index)
        }
    }

    // Not a data class: another tap on the mode being settled on is still a new request.
    internal class SettleRequest(
        val index: Int,
    )
}

@Composable
internal fun rememberModeSelectorState(initialIndex: Int): ModeSelectorState {
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)

    return remember {
        ModeSelectorState(
            initialIndex = initialIndex,
            hapticFeedback = currentHapticFeedback,
        )
    }
}

@Composable
internal fun ModeSelectorEffects(
    state: ModeSelectorState,
    labels: List<String>,
    selectedIndex: Int,
    onModeSelected: (index: Int) -> Unit,
) {
    val currentSelectedIndex = rememberUpdatedState(selectedIndex)
    val currentOnModeSelected = rememberUpdatedState(onModeSelected)

    SideEffect {
        state.anchor(
            labels = labels,
            selectedIndex = selectedIndex,
        )
    }

    DisposableEffect(state) {
        state.onSettled = { index ->
            if (index != currentSelectedIndex.value) {
                currentOnModeSelected.value(index)
            }
        }
        onDispose {
            state.settleNow()
            state.onSettled = {}
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.settleRequest.takeUnless { state.isDragging } }
            .collectLatest { request ->
                if (request != null) {
                    state.settle(request = request)
                }
            }
    }

    if (state.isIdle) {
        LaunchedEffect(state, selectedIndex, state.revision) {
            state.syncTo(index = selectedIndex)
        }
    }
}
