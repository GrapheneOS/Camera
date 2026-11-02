package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderPreviewControlsState as Controls
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ViewfinderPreviewControlsDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun showFocusPanels()

    fun showZoomPanel()
    fun hideZoomPanel()

    fun showExposurePanel()
    fun hideExposurePanel()
}

internal class ViewfinderPreviewControlsDelegateImpl @Inject constructor(
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderPreviewControlsDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

    private val zoomPanel = AutoHidingPanel { controls, shown ->
        controls.copy(isZoomPanelShown = shown)
    }

    private val exposurePanel = AutoHidingPanel { controls, shown ->
        controls.copy(isExposurePanelShown = shown)
    }

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        this.scope = scope
        this.stateHolder = stateHolder
    }

    override fun showFocusPanels() {
        exposurePanel.show()
        zoomPanel.show()
    }

    override fun showZoomPanel() {
        zoomPanel.show()
    }

    override fun hideZoomPanel() {
        zoomPanel.hide()
    }

    override fun showExposurePanel() {
        exposurePanel.show()
    }

    override fun hideExposurePanel() {
        exposurePanel.hide()
    }

    private inner class AutoHidingPanel(
        private val withShown: (Controls, Boolean) -> Controls,
    ) {

        private var timeout: Job? = null

        fun show() {
            setShown(true)

            timeout?.cancel()
            timeout = scope.launch(mainDispatcher) {
                delay(PANEL_VISIBILITY_DURATION)
                setShown(false)
            }
        }

        fun hide() {
            timeout?.cancel()
            setShown(false)
        }

        private fun setShown(shown: Boolean) {
            stateHolder.update { state ->
                state.copy(previewControls = withShown(state.previewControls, shown))
            }
        }
    }

    private companion object {
        private val PANEL_VISIBILITY_DURATION = 2.seconds
    }
}
