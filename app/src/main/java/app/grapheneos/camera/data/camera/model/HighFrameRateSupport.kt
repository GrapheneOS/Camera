package app.grapheneos.camera.data.camera.model

sealed interface HighFrameRateSupport {

    data object Supported : HighFrameRateSupport

    data class Unavailable(
        val limit: HighFrameRateLimit,
    ) : HighFrameRateSupport
}
