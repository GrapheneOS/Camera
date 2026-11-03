package app.grapheneos.camera.ui.viewfinder.screen

import android.view.View
import app.grapheneos.camera.ui.viewfinder.components.BottomTabLayout
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ModeTabsUiModel

internal class ViewfinderModeStripRenderer(
    private val tabs: BottomTabLayout,
    private val onAction: (ViewfinderAction) -> Unit,
) {

    private var renderedState: ModeTabsUiModel? = null

    fun render(state: ModeTabsUiModel) {
        val previous = renderedState
        renderedState = state
        tabs.visibility = when {
            state.visible -> View.VISIBLE
            else -> View.INVISIBLE
        }
        tabs.setModes(
            modes = state.availableModes,
            currentMode = state.selectedMode,
            onModeTouched = { mode -> onAction(CameraAction.ModeStripReleased(mode)) },
        )

        if (previous != null &&
            (state.selectionId != previous.selectionId || state.selectedMode != previous.selectedMode)
        ) {
            tabs.goToMode(state.selectedMode) {
                onAction(CameraAction.ModeStripSettled(state.selectedMode))
            }
        }
    }
}
