package app.grapheneos.camera.ui.viewfinder.screen.model.ui

data class ExposureUiModel(
    val range: IntRange? = null,
    val progress: Int = 0,
    val panelVisible: Boolean = false,
)
