package app.grapheneos.camera.data.media.model

import android.util.Size
import kotlin.time.Duration

internal data class CapturedItemDetails(
    val fileName: String,
    val relativePath: String?,
    val sizeBytes: Long?,
    val resolution: Size?,
    val duration: Duration?,
    val created: MediaTimestamp?,
    val modified: MediaTimestamp?,
)
