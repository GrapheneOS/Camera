package app.grapheneos.camera.data.camera.model

data class VideoFeatureGroup(
    val request: FeatureGroupRequest,
    val highFrameRate: HighFrameRateSupport? = null,
)
