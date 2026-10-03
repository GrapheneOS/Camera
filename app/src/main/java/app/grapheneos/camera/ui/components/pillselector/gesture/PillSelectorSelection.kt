package app.grapheneos.camera.ui.components.pillselector.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.gesture.DragEndInteractions
import app.grapheneos.camera.ui.components.pillselector.PillSelectorGeometry
import kotlin.math.roundToInt

@Stable
internal class PillSelectorSelection(
    initialIndex: Int,
    itemCount: Int,
    private val hapticFeedback: State<HapticFeedback>,
) {

    val geometry = PillSelectorGeometry(itemCount = itemCount)
    val dragState = DraggableState { pixels -> dragBy(pixels = pixels) }
    val dragInteractions: MutableInteractionSource = DragEndInteractions(
        onStop = { isDragCanceled = false },
        onCancel = { isDragCanceled = true },
    )

    val value: Float
        get() {
            return dragValue ?: animatable.value
        }

    val isDragging: Boolean
        get() {
            return dragValue != null
        }

    var revision by mutableIntStateOf(0)
        private set

    private val animatable = Animatable(initialValue = initialIndex.toFloat())
    private var dragValue by mutableStateOf<Float?>(null)
    private var isDragCanceled = false

    fun startDrag() {
        dragValue = animatable.value
    }

    suspend fun endDrag(onReleased: (index: Int) -> Unit) {
        val position = dragValue ?: return

        if (!isDragCanceled) {
            onReleased(position.roundToInt())
        }
        animatable.snapTo(targetValue = position)
        dragValue = null
        revision++
    }

    suspend fun animateTo(index: Int) {
        animatable.animateTo(
            targetValue = index.toFloat(),
            animationSpec = SELECTION_SPEC,
        )
    }

    private fun dragBy(pixels: Float) {
        val previous = dragValue ?: return
        val items = pixels / geometry.pitch(selection = previous)
        val next = (previous + items).coerceIn(0f, geometry.lastIndex.toFloat())

        dragValue = next
        if (next.roundToInt() != previous.roundToInt()) {
            hapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    private companion object {
        private val SELECTION_SPEC = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 500f,
        )
    }
}

@Composable
internal fun rememberPillSelectorSelection(
    selectedIndex: Int,
    itemCount: Int,
): PillSelectorSelection {
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)
    val selection = remember(itemCount) {
        PillSelectorSelection(
            initialIndex = selectedIndex,
            itemCount = itemCount,
            hapticFeedback = currentHapticFeedback,
        )
    }

    PillSelectorSyncEffect(
        selection = selection,
        selectedIndex = selectedIndex,
    )

    return selection
}

@Composable
private fun PillSelectorSyncEffect(
    selection: PillSelectorSelection,
    selectedIndex: Int,
) {
    if (!selection.isDragging) {
        LaunchedEffect(selection, selectedIndex, selection.revision) {
            selection.animateTo(index = selectedIndex)
        }
    }
}
