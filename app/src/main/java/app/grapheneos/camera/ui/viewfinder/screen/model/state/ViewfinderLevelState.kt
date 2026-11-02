package app.grapheneos.camera.ui.viewfinder.screen.model.state

data class ViewfinderLevelState(
    val visible: Boolean = false,
    val tiltDegrees: Int = 0,
    val horizonDegrees: Int = 0,
)
