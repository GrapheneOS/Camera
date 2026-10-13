package app.grapheneos.camera.data.camera.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoBitrates(
    val videoBitsPerSecond: Int,
    val videoFrameRate: Int,
    val audioBitsPerSecond: Int,
) : Parcelable
