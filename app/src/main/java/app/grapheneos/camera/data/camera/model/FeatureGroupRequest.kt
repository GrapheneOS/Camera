package app.grapheneos.camera.data.camera.model

import androidx.camera.core.featuregroup.GroupableFeature

sealed interface FeatureGroupRequest {

    data object Unused : FeatureGroupRequest

    data class Requested(
        val videoQualityFeature: GroupableFeature?,
        val stabilization: Boolean,
        val hdr: Boolean,
    ) : FeatureGroupRequest
}
