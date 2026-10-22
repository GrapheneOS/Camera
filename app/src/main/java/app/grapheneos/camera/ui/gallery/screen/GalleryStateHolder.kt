package app.grapheneos.camera.ui.gallery.screen

import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

internal class GalleryStateHolder(
    initial: GalleryState,
) {

    private val derivations = mutableListOf<(GalleryState) -> Unit>()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    fun <T> derive(map: (GalleryState) -> T): StateFlow<T> {
        val derived = MutableStateFlow(map(_state.value))
        derivations += { state -> derived.value = map(state) }

        return derived.asStateFlow()
    }

    fun update(transform: (GalleryState) -> GalleryState) {
        _state.update(transform)

        val state = _state.value
        derivations.forEach { derivation -> derivation(state) }
    }

    fun send(effect: Effect) {
        if (effect is Effect.OtherApp && _state.value.isSecureSession) return

        _effects.trySend(effect)
    }
}
