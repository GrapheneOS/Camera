package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ViewfinderPreviewControlsDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun showExposurePanel()
    fun hideExposurePanel()
}

internal class ViewfinderPreviewControlsDelegateImpl @Inject constructor() :
    ViewfinderPreviewControlsDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

    private var timeout: Job? = null

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        this.scope = scope
        this.stateHolder = stateHolder
    }

    override fun showExposurePanel() {
        setShown(true)
        timeout?.cancel()
        timeout = scope.launch {
            delay(PANEL_VISIBILITY_DURATION)
            setShown(false)
        }
    }

    override fun hideExposurePanel() {
        timeout?.cancel()
        setShown(false)
    }

    private fun setShown(shown: Boolean) {
        stateHolder.update { state ->
            state.copy(previewControls = state.previewControls.copy(isExposurePanelShown = shown))
        }
    }

    private companion object {
        private val PANEL_VISIBILITY_DURATION = 2.seconds
    }
}
