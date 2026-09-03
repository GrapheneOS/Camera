package app.grapheneos.camera.data.media.store

import androidx.datastore.core.Serializer
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The current tree and displaced grants share a file so changing location updates both atomically.
@Serializable
internal data class StoragePrefs(
    @SerialName("storage_location")
    val storageLocation: String? = null,
    @SerialName("previous_saf_trees")
    val previousSafTrees: List<String> = emptyList(),
    @SerialName("legacy_media_uris")
    val legacyMediaUris: String? = null,
)

internal val storagePrefsSerializer: Serializer<StoragePrefs> = JsonPreferenceSerializer(
    serializer = StoragePrefs.serializer(),
    defaultValue = StoragePrefs(),
)
