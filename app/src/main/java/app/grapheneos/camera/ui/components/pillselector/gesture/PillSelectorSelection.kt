package app.grapheneos.camera.ui.components.pillselector.gesture

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.gesture.DragEndInteractions
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import app.grapheneos.camera.ui.components.pillselector.PillSelectorGeometry
import kotlin.math.roundToInt

@Stable
internal class PillSelectorSelection<Key>(
    initialIndex: Int,
    keys: List<Key>,
    private val hapticFeedback: State<HapticFeedback>,
) {

    val geometry = PillSelectorGeometry(itemCount = keys.size)
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

    private var animatable by mutableStateOf(Animatable(initialValue = initialIndex.toFloat()))
    private var keys by mutableStateOf(keys)
    private var dragValue by mutableStateOf<Float?>(null)
    private var isDragCanceled = false

    fun rebase(
        index: Int,
        keys: List<Key>,
    ) {
        Snapshot.withoutReadObservation {
            if (keys == this.keys) return@withoutReadObservation

            val position = positionIn(
                newKeys = keys,
                selectedIndex = index,
            )
            this.keys = keys
            geometry.resize(itemCount = keys.size)
            animatable = Animatable(initialValue = position)

            if (isDragging) {
                dragValue = position
            }

            revision++
        }
    }

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
            animationSpec = SETTLE_SPEC,
        )
    }

    private fun positionIn(
        newKeys: List<Key>,
        selectedIndex: Int,
    ): Float {
        val position = value
        val anchorIndex = position.roundToInt().coerceIn(keys.indices)
        val newAnchorIndex = newKeys.indexOf(keys[anchorIndex])
        if (newAnchorIndex < 0) {
            return selectedIndex.toFloat()
        }

        val offsetFromAnchor = position - anchorIndex
        return (newAnchorIndex + offsetFromAnchor)
            .coerceIn(0f, newKeys.lastIndex.toFloat())
    }

    private fun dragBy(pixels: Float) {
        val previous = dragValue ?: return
        val pitch = geometry.pitch(selection = previous)
        if (pitch == 0f) return

        val items = pixels / pitch
        val next = (previous + items).coerceIn(0f, geometry.lastIndex.toFloat())

        dragValue = next
        if (next.roundToInt() != previous.roundToInt()) {
            hapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}

@Composable
internal fun <Key> rememberPillSelectorSelection(
    selectedIndex: Int,
    keys: List<Key>,
): PillSelectorSelection<Key> {
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)
    val selection = remember {
        PillSelectorSelection(
            initialIndex = selectedIndex,
            keys = keys,
            hapticFeedback = currentHapticFeedback,
        )
    }

    selection.rebase(
        index = selectedIndex,
        keys = keys,
    )

    if (!selection.isDragging) {
        LaunchedEffect(selection, selectedIndex, selection.revision) {
            selection.animateTo(index = selectedIndex)
        }
    }

    return selection
}
