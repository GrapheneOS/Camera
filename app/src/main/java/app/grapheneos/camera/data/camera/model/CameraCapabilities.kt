package app.grapheneos.camera.data.camera.model

import android.os.Parcelable
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraCapabilities(
    val lensFacing: LensFacing = LensFacing.BACK,
    val lensFeatures: Map<LensFacing, LensFeatures> = emptyMap(),
    val photoMegapixels: Int? = null,
    val photoResolutions: PhotoResolutions? = null,
    val videoBitrates: Map<VideoQuality, VideoBitrates> = emptyMap(),
) : Parcelable
