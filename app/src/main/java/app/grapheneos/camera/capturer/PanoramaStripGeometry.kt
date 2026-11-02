package app.grapheneos.camera.capturer

/** Panorama overlay layout calculations. */
internal object PanoramaStripGeometry {
    fun thumbnailLeft(viewLeft: Float, tileWidth: Float,
                      index: Int, overflow: Float): Float =
        viewLeft + index * tileWidth - overflow

    /** Size the mosaic preview to the available width. */
    fun previewFillWidth(viewWidth: Float, fraction: Float, minimumWidth: Float): Float {
        if (!viewWidth.isFinite() || viewWidth <= 0f) return 0f
        val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return (viewWidth * safeFraction).coerceAtLeast(
            minimumWidth.coerceAtLeast(0f)).coerceAtMost(viewWidth)
    }

    fun guideLeft(viewLeft: Float, viewWidth: Float, guideWidth: Float,
                  inset: Float, fraction: Float): Float {
        val travel = (viewWidth - guideWidth - 2f * inset).coerceAtLeast(0f)
        return viewLeft + inset + travel * fraction.coerceIn(0f, 1f)
    }

    fun progressRight(barLeft: Float, barWidth: Float, fraction: Float): Float =
        barLeft + barWidth * fraction.coerceIn(0f, 1f)
}
