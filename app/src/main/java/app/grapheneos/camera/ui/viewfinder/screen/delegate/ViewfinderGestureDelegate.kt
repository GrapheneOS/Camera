package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject

interface ViewfinderGestureDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun onPreviewSwiped(direction: SwipeDirection)
}

internal class ViewfinderGestureDelegateImpl @Inject constructor(
    private val swipeOutcomeMapper: SwipeOutcomeMapper,
) : ViewfinderGestureDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun onPreviewSwiped(direction: SwipeDirection) {
        val outcome = swipeOutcomeMapper.map(
            direction = direction,
            state = stateHolder.state.value,
        )

        when (outcome) {
            null -> Unit

            is SwipeOutcome.ShowQrFormats -> {
                stateHolder.postEffect(Effect.Settings.ShowQrFormats)
            }

            is SwipeOutcome.OpenSettingsPanel -> {
                stateHolder.postNavEvent(NavEvent.OpenSettingsPanel)
            }

            is SwipeOutcome.CloseSettingsPanel -> {
                stateHolder.postNavEvent(NavEvent.CloseSettingsPanel)
            }

            is SwipeOutcome.SelectAdjacentMode -> {
                stateHolder.postEffect(Effect.ModeTab.SelectAdjacent(offset = outcome.offset))
            }
        }
    }
}
