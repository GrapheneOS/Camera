package app.grapheneos.camera.data.camera.model

data class CameraExposure(
    val compensationIndex: Int,
    val compensationRange: IntRange,
    val compensationStep: Float,
    val isCompensationSupported: Boolean,
) {

    fun isCompensated(): Boolean {
        return compensationIndex != 0
    }

    fun isAdjustable(): Boolean {
        return isCompensationSupported && compensationRange.first < compensationRange.last &&
            compensationStep.isFinite() && compensationStep > 0f
    }
}
