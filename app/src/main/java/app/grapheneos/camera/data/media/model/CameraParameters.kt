package app.grapheneos.camera.data.media.model

internal data class CameraParameters(
    val make: String?,
    val model: String?,
    val fNumber: Double?,
    val exposureSeconds: Double?,
    val focalLengthMillimeters: Double?,
    val iso: Int?,
) {

    fun isEmpty(): Boolean {
        return listOf(
            make,
            model,
            fNumber,
            exposureSeconds,
            focalLengthMillimeters,
            iso,
        ).all { it == null }
    }
}
