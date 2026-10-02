package app.grapheneos.camera.ui.components.modeselector.gesture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.modeselector.ModeSelectorGeometry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

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

    var settlingIndex by mutableStateOf<Int?>(null)
        private set

    internal var geometry by mutableStateOf(
        ModeSelectorGeometry(
            itemWidths = emptyList(),
            containerWidth = 0f,
            highlightInset = 0f,
        ),
    )

    internal var onSettled: (Int) -> Unit = {}

    val isIdle: Boolean
        get() {
            return !isDragging && settlingIndex == null
        }

    private val mutex = MutatorMutex()

    fun settleNow() {
        val index = settlingIndex ?: return

        position = index.toFloat()
        finishSettle(index = index)
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
            settlingIndex = null
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

    internal fun release() {
        settlingIndex = geometry.nearestIndex(position = position)
        isDragging = false
    }

    internal fun select(index: Int) {
        settlingIndex = index
    }

    internal suspend fun settle(index: Int) {
        animateTo(
            index = index,
            ticks = true,
        ) { settlingIndex == index }
        finishSettle(index = index)
    }

    internal suspend fun syncTo(index: Int) {
        animateTo(
            index = index,
            ticks = false,
        ) { true }
    }

    private suspend fun animateTo(
        index: Int,
        ticks: Boolean,
        isCurrent: () -> Boolean,
    ) {
        mutex.mutate {
            animate(
                initialValue = position,
                targetValue = index.toFloat(),
                animationSpec = SETTLE_SPEC,
            ) { value, _ ->
                if (isCurrent()) {
                    moveTo(
                        newPosition = value,
                        ticks = ticks,
                    )
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

    private fun finishSettle(index: Int) {
        if (settlingIndex == index) {
            settlingIndex = null
            onSettled(index)
        }
    }

    private companion object {
        private val SETTLE_SPEC = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 500f,
        )
    }
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
    selectedIndex: Int,
    onModeSelected: (index: Int) -> Unit,
) {
    val currentSelectedIndex = rememberUpdatedState(selectedIndex)
    val currentOnModeSelected = rememberUpdatedState(onModeSelected)
    val syncTarget = when {
        state.isIdle -> selectedIndex
        else -> null
    }
    val currentSyncTarget = rememberUpdatedState(syncTarget)

    DisposableEffect(state) {
        state.onSettled = { index ->
            if (index != currentSelectedIndex.value) {
                currentOnModeSelected.value(index)
            }
        }
        onDispose {
            state.onSettled = {}
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settlingIndex }
            .collectLatest { index ->
                if (index != null) {
                    state.settle(index = index)
                }
            }
    }
    LaunchedEffect(state) {
        snapshotFlow { currentSyncTarget.value }
            .filterNotNull()
            .collectLatest { index -> state.syncTo(index = index) }
    }
}
