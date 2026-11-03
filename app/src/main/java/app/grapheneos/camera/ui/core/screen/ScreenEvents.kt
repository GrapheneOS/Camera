package app.grapheneos.camera.ui.core.screen

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class ScreenEvents<Event> {

    private val channel = Channel<Event>(capacity = Channel.BUFFERED)
    val flow: Flow<Event> = channel.receiveAsFlow()

    fun send(event: Event) {
        channel.trySend(event)
    }
}
