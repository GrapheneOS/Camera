package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

@Composable
internal fun rememberSheetNestedScrollConnection(
    state: QuickSettingsSheetState,
    onSettle: (isShown: Boolean) -> Unit,
): NestedScrollConnection {
    val currentOnSettle by rememberUpdatedState(onSettle)

    return remember(state) {
        SheetNestedScrollConnection(
            state = state,
            onSettle = { isShown -> currentOnSettle(isShown) },
        )
    }
}

private class SheetNestedScrollConnection(
    private val state: QuickSettingsSheetState,
    private val onSettle: (isShown: Boolean) -> Unit,
) : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            state.isDragging && available.y < 0f -> Offset(x = 0f, y = state.drag(available.y))
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

        if (!state.isDragging) {
            state.startDrag()
        }

        return Offset(x = 0f, y = state.drag(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!state.isDragging) return Velocity.Zero

        onSettle(state.release(available.y))

        return available
    }
}
