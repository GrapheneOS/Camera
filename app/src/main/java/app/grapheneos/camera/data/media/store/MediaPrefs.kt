package app.grapheneos.camera.data.media.store

import androidx.datastore.core.Serializer
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MediaPrefs(
    @SerialName("last_captured_item") val lastCapturedItem: StoredCapturedItem? = null,
)

@Serializable
internal data class StoredCapturedItem(
    @SerialName("type")
    val type: Int,
    @SerialName("date_string")
    val dateString: String,
    @SerialName("uri")
    val uri: String,
)

internal val mediaPrefsSerializer: Serializer<MediaPrefs> = JsonPreferenceSerializer(
    serializer = MediaPrefs.serializer(),
    defaultValue = MediaPrefs(),
)
