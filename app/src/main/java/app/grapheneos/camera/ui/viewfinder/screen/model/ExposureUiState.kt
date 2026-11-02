package app.grapheneos.camera.ui.viewfinder.screen.model

data class ExposureUiState(
    val range: IntRange? = null,
    val progress: Int = 0,
    val panelVisible: Boolean = false,
)
