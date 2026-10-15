package app.grapheneos.camera.data.camera.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LensFeatures(
    val isZslSupported: Boolean = false,
    val isVideoStabilizationSupported: Boolean = false,
    val isHdrVideoSupported: Boolean = false,
    val isUltraHdrSupported: Boolean = false,
) : Parcelable
