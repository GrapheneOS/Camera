package app.grapheneos.camera.portrait

/**
 * Select the largest *native* Camera2 JPEG size for any selfie camera.
 * Do not assume 4:3, 16:9, a device model or a marketed sensor resolution.
 * The final binding is still negotiated by CameraX and may be smaller.
 */
object PortraitFrontResolutionPreference {
    fun chooseNativeJpegSize(candidates: Collection<Pair<Int, Int>>): Pair<Int, Int>? =
        candidates.asSequence()
            .filter { (width, height) -> width > 0 && height > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { (w, h) -> w.toLong() * h.toLong() }
                .thenBy { (w, h) -> maxOf(w, h) })
}
