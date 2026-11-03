package app.grapheneos.camera.data.camera.model

import androidx.camera.core.featuregroup.GroupableFeature
import app.grapheneos.camera.data.core.model.LensFacing

data class HighFrameRateProbeKey(
    val lensFacing: LensFacing,
    val videoQualityFeature: GroupableFeature?,
    val hdrVideo: Boolean,
)
