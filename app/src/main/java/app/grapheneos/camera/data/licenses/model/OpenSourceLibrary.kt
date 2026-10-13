package app.grapheneos.camera.data.licenses.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenSourceLibrary(
    val name: String,
    val coordinates: String,
    val license: String,
    val url: String? = null,
)
