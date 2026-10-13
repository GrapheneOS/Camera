package app.grapheneos.camera.data.licenses.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenSourceLicenses(
    val libraries: List<OpenSourceLibrary> = emptyList(),
    val licenses: Map<String, String> = emptyMap(),
)
