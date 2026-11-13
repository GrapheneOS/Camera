package app.grapheneos.camera.data.camera.model

import androidx.camera.core.featuregroup.GroupableFeature

data class HighFrameRateProbeKey(
    val lensFacing: LensFacing,
    val videoQualityFeature: GroupableFeature?,
    val hdrVideo: Boolean,
)
