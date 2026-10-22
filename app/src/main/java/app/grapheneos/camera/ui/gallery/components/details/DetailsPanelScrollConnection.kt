package app.grapheneos.camera.ui.gallery.components.details

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import app.grapheneos.camera.ui.gallery.components.gesture.DetailsPanelState

internal class DetailsPanelScrollConnection(
    private val detailsState: DetailsPanelState,
    private val onShownChange: (Boolean) -> Unit,
) : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            source == NestedScrollSource.UserInput && available.y < 0f -> drag(available)
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            source == NestedScrollSource.UserInput && available.y != 0f -> drag(available)
            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return when {
            detailsState.isDragging -> {
                onShownChange(detailsState.release(available.y))
                available
            }
            else -> Velocity.Zero
        }
    }

    private fun drag(available: Offset): Offset {
        val isStarting = !detailsState.isDragging

        if (isStarting) {
            detailsState.startDrag()
        }

        val consumed = detailsState.drag(available.y)

        if (isStarting && consumed == 0f) {
            detailsState.cancelDrag()
        }

        return Offset(
            x = 0f,
            y = consumed,
        )
    }
}
