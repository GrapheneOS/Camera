package app.grapheneos.camera.ui.core.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

@Composable
internal fun <T> CollectEvents(
    events: Flow<T>,
    onEvent: suspend (T) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(events) {
        events.collect { event -> currentOnEvent(event) }
    }
}
