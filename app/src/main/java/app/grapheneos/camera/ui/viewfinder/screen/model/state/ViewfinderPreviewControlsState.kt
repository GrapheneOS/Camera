package app.grapheneos.camera.ui.viewfinder.screen.model.state

data class ViewfinderPreviewControlsState(
    val focusRequest: ViewfinderFocusRequest? = null,
    val isZoomPanelShown: Boolean = false,
    val isExposurePanelShown: Boolean = false,
)
