package app.grapheneos.camera.data.camera.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PhotoResolutions(
    val standardMegapixels: Int,
    val fullMegapixels: Int,
) : Parcelable
