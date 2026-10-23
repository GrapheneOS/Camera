package app.grapheneos.camera.capturer

/** Tracks image motion from downsampled luminance frames. */
internal class PanoramaFlowTracker {
    data class Motion(val dx: Float, val dy: Float, val reliable: Boolean, val sharpness: Float,
                      val inlierFraction: Float, val tracked: Int)
    private val tracker = PanoramaMotionTracker(WIDTH, HEIGHT)
    fun reset() { tracker.reset() }
    fun track(sample: ByteArray): Motion {
        val motion = tracker.track(sample)
        return Motion(motion.dx, motion.dy, motion.reliable,
            motion.sharpness, motion.inlierFraction, motion.tracked)
    }
    companion object {
        const val WIDTH = 96
        const val HEIGHT = 128
    }
}
