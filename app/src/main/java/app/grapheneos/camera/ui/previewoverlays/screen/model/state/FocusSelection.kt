package app.grapheneos.camera.ui.previewoverlays.screen.model.state

internal data class FocusSelection(
    val requestId: Long,
    val x: Float,
    val y: Float,
    val phase: FocusPhase = FocusPhase.Focusing,
    val visible: Boolean = true,
) {

    fun isNear(
        x: Float,
        y: Float,
        radius: Float,
    ): Boolean {
        val distanceX = this.x - x
        val distanceY = this.y - y
        return distanceX * distanceX + distanceY * distanceY <= radius * radius
    }
}
