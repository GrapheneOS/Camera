package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

@Composable
internal fun KeepAtEndEffect(listState: LazyListState) {
    LaunchedEffect(listState) {
        var isAtEnd = false

        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .filter { listState.layoutInfo.totalItemsCount > 0 }
            .collect { (isScrolling, canScrollForward) ->
                when {
                    !isScrolling && isAtEnd && canScrollForward -> {
                        listState.scrollBy(distanceToEnd(listState))
                    }

                    else -> isAtEnd = !canScrollForward
                }
            }
    }
}

private fun distanceToEnd(listState: LazyListState): Float {
    val layoutInfo = listState.layoutInfo
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return 0f
    val contentEnd = lastItem.offset + lastItem.size + layoutInfo.afterContentPadding

    return (contentEnd - layoutInfo.viewportEndOffset).coerceAtLeast(0).toFloat()
}
