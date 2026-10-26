package app.grapheneos.camera.ui.gallery.screen.delegate

import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

internal interface GalleryControlsDelegate {

    fun bind(stateHolder: GalleryStateHolder)

    suspend fun hideIdleControls()

    fun toggle()

    fun hide()

    fun restartIdleTimer()
}

internal class GalleryControlsDelegateImpl @Inject constructor() : GalleryControlsDelegate {

    private val idleTimerRestarts = Channel<Unit>(capacity = Channel.CONFLATED)

    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun hideIdleControls() {
        val canHideControls = stateHolder.state
            .map(GalleryState::canHideControls)
            .distinctUntilChanged()
        val restarts = idleTimerRestarts.receiveAsFlow().onStart { emit(Unit) }

        combine(
            canHideControls,
            restarts,
        ) { canHide, _ -> canHide }
            .collectLatest { canHide ->
                if (canHide) {
                    delay(IDLE_TIMEOUT)
                    hide()
                }
            }
    }

    override fun toggle() {
        stateHolder.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    override fun hide() {
        stateHolder.update { it.copy(areControlsVisible = false) }
    }

    override fun restartIdleTimer() {
        idleTimerRestarts.trySend(Unit)
    }

    private companion object {
        val IDLE_TIMEOUT = 5.seconds
    }
}
