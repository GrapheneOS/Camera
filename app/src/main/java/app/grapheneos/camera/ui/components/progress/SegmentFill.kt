package app.grapheneos.camera.ui.components.progress

internal data class SegmentFill(
    val sweep: Float,
    val alpha: Float,
) {

    companion object {
        fun of(
            fill: Float,
            sweep: Float,
            cap: Float,
        ): SegmentFill {
            val caps = cap * 2
            val visible = (sweep + caps) * fill

            return when {
                visible >= caps -> SegmentFill(
                    sweep = visible - caps,
                    alpha = 1f,
                )

                else -> SegmentFill(
                    sweep = 0f,
                    alpha = visible / caps,
                )
            }
        }
    }
}
